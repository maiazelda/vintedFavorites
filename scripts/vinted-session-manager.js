#!/usr/bin/env node
/**
 * Vinted Session Manager
 * Automatically logs into Vinted and extracts session cookies/tokens
 *
 * Usage: node vinted-session-manager.js [--email EMAIL] [--password PASSWORD] [--api-url URL]
 */

const { chromium } = require('playwright');
const http = require('http');
const https = require('https');

// Configuration
const config = {
    email: process.env.VINTED_EMAIL || process.argv.find((arg, i) => process.argv[i-1] === '--email'),
    password: process.env.VINTED_PASSWORD || process.argv.find((arg, i) => process.argv[i-1] === '--password'),
    apiUrl: process.env.API_URL || process.argv.find((arg, i) => process.argv[i-1] === '--api-url') || 'http://localhost:8080',
    headless: process.env.HEADLESS !== 'false',
    vintedUrl: 'https://www.vinted.fr'
};

async function extractSessionData(page, context) {
    console.log('Extracting session data...');

    // Get all cookies
    const cookies = await context.cookies();
    const cookieString = cookies
        .filter(c => c.domain.includes('vinted'))
        .map(c => `${c.name}=${c.value}`)
        .join('; ');

    // Extract CSRF token from meta tag
    const csrfToken = await page.evaluate(() => {
        const meta = document.querySelector('meta[name="csrf-token"]');
        return meta ? meta.getAttribute('content') : null;
    });

    // Extract anon-id from cookies or generate from existing
    const anonIdCookie = cookies.find(c => c.name === 'anon_id' || c.name === '_vinted_fr_anon_id');
    const anonId = anonIdCookie ? anonIdCookie.value : null;

    // Also try to get it from the page
    const anonIdFromPage = await page.evaluate(() => {
        // Try to find in window object or cookies
        if (window.__INITIAL_STATE__?.user?.anon_id) {
            return window.__INITIAL_STATE__.user.anon_id;
        }
        return null;
    });

    return {
        rawCookies: cookieString,
        csrfToken: csrfToken,
        anonId: anonId || anonIdFromPage,
        cookieCount: cookies.filter(c => c.domain.includes('vinted')).length
    };
}

async function sendToApi(sessionData) {
    console.log('Sending session data to API...');

    const data = JSON.stringify(sessionData);
    const url = new URL(`${config.apiUrl}/api/vinted/cookies`);

    const options = {
        hostname: url.hostname,
        port: url.port || (url.protocol === 'https:' ? 443 : 80),
        path: url.pathname,
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            'Content-Length': Buffer.byteLength(data)
        }
    };

    return new Promise((resolve, reject) => {
        const protocol = url.protocol === 'https:' ? https : http;
        const req = protocol.request(options, (res) => {
            let body = '';
            res.on('data', chunk => body += chunk);
            res.on('end', () => {
                try {
                    const result = JSON.parse(body);
                    console.log('API Response:', result);
                    resolve(result);
                } catch (e) {
                    resolve({ success: true, raw: body });
                }
            });
        });

        req.on('error', (e) => {
            console.error('API Error:', e.message);
            reject(e);
        });

        req.write(data);
        req.end();
    });
}

async function login(page) {
    console.log('Navigating to Vinted login page...');

    // Go to Vinted
    await page.goto(config.vintedUrl, { waitUntil: 'networkidle' });

    // Wait a bit for any popups/consent dialogs
    await page.waitForTimeout(2000);

    // Handle cookie consent if present
    try {
        const acceptButton = await page.$('[data-testid="cookie-consent-accept-all"], #onetrust-accept-btn-handler, .cookie-consent-accept');
        if (acceptButton) {
            console.log('Accepting cookies consent...');
            await acceptButton.click();
            await page.waitForTimeout(1000);
        }
    } catch (e) {
        // No consent dialog, continue
    }

    // Check if already logged in
    const isLoggedIn = await page.evaluate(() => {
        return !!document.querySelector('[data-testid="header--avatar"], .Header__user-avatar, [class*="avatar"]');
    });

    if (isLoggedIn) {
        console.log('Already logged in!');
        return true;
    }

    // STEP 1: Click on "S'inscrire | Se connecter" button in header
    console.log('Step 1: Clicking login button in header...');
    try {
        const headerLoginSelectors = [
            '[data-testid="header--login-button"]',
            'a:has-text("S\'inscrire")',
            'a:has-text("Se connecter")',
            'button:has-text("S\'inscrire")',
            'button:has-text("Se connecter")',
            'a[href*="login"]',
            '.Header__login'
        ];

        let clicked = false;
        for (const selector of headerLoginSelectors) {
            try {
                const loginBtn = await page.$(selector);
                if (loginBtn && await loginBtn.isVisible()) {
                    console.log(`Found header login button with selector: ${selector}`);
                    await loginBtn.click();
                    clicked = true;
                    break;
                }
            } catch (e) {}
        }

        if (!clicked) {
            console.log('Could not find login button, trying direct URL...');
            await page.goto(`${config.vintedUrl}/member/login`, { waitUntil: 'networkidle' });
        }

        await page.waitForTimeout(2000);
    } catch (e) {
        console.log('Error clicking header login button:', e.message);
        await page.goto(`${config.vintedUrl}/member/login`, { waitUntil: 'networkidle' });
    }

    // STEP 2: Handle OAuth page - click on "Se connecter" link or "e-mail" link
    console.log('Step 2: Checking for OAuth/intermediate page...');
    await page.waitForTimeout(1500);

    // Check if we're on the OAuth page (has Google/Apple/Facebook buttons)
    const isOAuthPage = await page.evaluate(() => {
        const pageText = document.body.innerText;
        return pageText.includes('Continuer avec Google') ||
               pageText.includes('Continuer avec Apple') ||
               pageText.includes('Continuer avec Facebook') ||
               pageText.includes('Bienvenue') ||
               pageText.includes('Rejoins le mouvement');
    });

    if (isOAuthPage) {
        console.log('OAuth page detected, looking for email login link...');

        // Try to click on "Se connecter" or "e-mail" link to go to email form
        const emailLoginSelectors = [
            'a:has-text("e-mail")',
            'a:has-text("E-mail")',
            'a:has-text("ton e-mail")',
            'a:has-text("ton adresse e-mail")',
            // The "Se connecter" link for existing users (different from the OAuth buttons)
            'a:has-text("Se connecter"):not(:has-text("Continuer"))',
            'text=Se connecter >> nth=-1', // Last "Se connecter" link on page
            'a[href*="login"]'
        ];

        let emailLinkClicked = false;
        for (const selector of emailLoginSelectors) {
            try {
                console.log(`Trying email link selector: ${selector}`);
                const emailLink = await page.$(selector);
                if (emailLink && await emailLink.isVisible()) {
                    console.log(`Found email login link with selector: ${selector}`);
                    await emailLink.click();
                    emailLinkClicked = true;
                    break;
                }
            } catch (e) {
                console.log(`Selector ${selector} failed: ${e.message}`);
            }
        }

        if (!emailLinkClicked) {
            // Try a more specific approach - look for the link at the bottom of the modal
            console.log('Trying to find email link by text content...');
            try {
                // Look for links containing "mail" text
                await page.click('a:text-matches("mail", "i")');
                emailLinkClicked = true;
            } catch (e) {
                console.log('Could not find email link by text match');
            }
        }

        if (!emailLinkClicked) {
            // Last resort: try clicking "Se connecter" link (for existing users)
            try {
                const allLinks = await page.$$('a');
                for (const link of allLinks) {
                    const text = await link.textContent();
                    if (text && text.trim() === 'Se connecter') {
                        console.log('Found "Se connecter" link for existing users');
                        await link.click();
                        emailLinkClicked = true;
                        break;
                    }
                }
            } catch (e) {
                console.log('Could not find Se connecter link');
            }
        }

        if (!emailLinkClicked) {
            console.log('WARNING: Could not find email login link on OAuth page');
            await page.screenshot({ path: 'oauth-page-debug.png', fullPage: true });
            console.log('Screenshot saved to oauth-page-debug.png for debugging');
        }

        await page.waitForTimeout(2000);
    }

    // STEP 3: Now we should be on the login form page
    console.log('Step 3: Filling login form...');
    await page.waitForTimeout(1000);

    // Try to find and fill email field
    const emailSelectors = [
        'input[name="email"]',
        'input[type="email"]',
        'input[data-testid="email-input"]',
        'input[autocomplete="email"]',
        '#email',
        'input[placeholder*="mail"]',
        'input[placeholder*="Mail"]',
        'input[placeholder*="E-mail"]',
        'input[placeholder*="Identifiant"]',
        'input[placeholder*="identifiant"]',
        'input[placeholder*="adresse"]'
    ];

    let emailFilled = false;
    for (const selector of emailSelectors) {
        try {
            console.log(`Trying email selector: ${selector}`);
            await page.waitForSelector(selector, { timeout: 3000 });
            const emailInput = await page.$(selector);
            if (emailInput && await emailInput.isVisible()) {
                await emailInput.click();
                await page.waitForTimeout(500);
                await emailInput.fill(config.email);
                emailFilled = true;
                console.log(`Email filled using selector: ${selector}`);
                break;
            }
        } catch (e) {
            console.log(`Selector ${selector} not found`);
        }
    }

    if (!emailFilled) {
        console.error('Could not find email input field');
        console.log('Saving debug screenshot...');
        await page.screenshot({ path: 'email-field-not-found.png', fullPage: true });
        return false;
    }

    // Wait a bit before password
    await page.waitForTimeout(500);

    // Try to find and fill password field
    const passwordSelectors = [
        'input[name="password"]',
        'input[type="password"]',
        'input[data-testid="password-input"]',
        'input[autocomplete="current-password"]',
        '#password'
    ];

    let passwordFilled = false;
    for (const selector of passwordSelectors) {
        try {
            console.log(`Trying password selector: ${selector}`);
            await page.waitForSelector(selector, { timeout: 3000 });
            const passwordInput = await page.$(selector);
            if (passwordInput && await passwordInput.isVisible()) {
                await passwordInput.click();
                await page.waitForTimeout(500);
                await passwordInput.fill(config.password);
                passwordFilled = true;
                console.log(`Password filled using selector: ${selector}`);
                break;
            }
        } catch (e) {
            console.log(`Selector ${selector} not found`);
        }
    }

    if (!passwordFilled) {
        console.error('Could not find password input field');
        console.log('Saving debug screenshot...');
        await page.screenshot({ path: 'password-field-not-found.png', fullPage: true });
        return false;
    }

    // Submit the form
    console.log('Submitting login form...');
    const submitSelectors = [
        'button[type="submit"]',
        'button:has-text("Continuer")',
        'button:has-text("Se connecter")',
        'button:has-text("Connexion")',
        '[data-testid="login-submit"]'
    ];

    for (const selector of submitSelectors) {
        try {
            const submitBtn = await page.$(selector);
            if (submitBtn) {
                await submitBtn.click();
                break;
            }
        } catch (e) {}
    }

    // Wait for login to complete
    console.log('Waiting for login to complete...');
    await page.waitForTimeout(5000);

    // Check if login was successful
    const loginSuccess = await page.evaluate(() => {
        return !!document.querySelector('[data-testid="header--avatar"], .Header__user-avatar, [class*="avatar"]') ||
               window.location.pathname === '/';
    });

    if (loginSuccess) {
        console.log('Login successful!');
        return true;
    } else {
        console.error('Login may have failed. Check for CAPTCHA or 2FA.');
        return false;
    }
}

async function refreshSession() {
    console.log('Starting Vinted session refresh...');
    console.log(`API URL: ${config.apiUrl}`);
    console.log(`Headless: ${config.headless}`);

    if (!config.email || !config.password) {
        console.error('ERROR: Email and password are required!');
        console.log('Usage: node vinted-session-manager.js --email YOUR_EMAIL --password YOUR_PASSWORD');
        console.log('Or set VINTED_EMAIL and VINTED_PASSWORD environment variables');
        process.exit(1);
    }

    const browser = await chromium.launch({
        headless: config.headless,
        args: [
            '--disable-blink-features=AutomationControlled',
            '--no-sandbox',
            '--disable-setuid-sandbox'
        ]
    });

    const context = await browser.newContext({
        userAgent: 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36',
        viewport: { width: 1920, height: 1080 },
        locale: 'fr-FR'
    });

    const page = await context.newPage();

    try {
        // Login
        const loginSuccess = await login(page);

        if (!loginSuccess) {
            console.error('Login failed!');
            // Take a screenshot for debugging
            await page.screenshot({ path: 'login-failed.png' });
            console.log('Screenshot saved to login-failed.png');
            process.exit(1);
        }

        // Navigate to favorites page to ensure we have all necessary cookies
        console.log('Navigating to favorites page...');
        await page.goto(`${config.vintedUrl}/member/items/favourites`, { waitUntil: 'networkidle' });
        await page.waitForTimeout(2000);

        // Extract session data
        const sessionData = await extractSessionData(page, context);

        console.log(`Extracted ${sessionData.cookieCount} cookies`);
        console.log(`CSRF Token: ${sessionData.csrfToken ? 'Found' : 'Not found'}`);
        console.log(`Anon ID: ${sessionData.anonId ? 'Found' : 'Not found'}`);

        // Send to API
        const result = await sendToApi(sessionData);

        if (result.success) {
            console.log('Session refreshed successfully!');
        } else {
            console.error('Failed to update session in API');
        }

    } catch (error) {
        console.error('Error:', error.message);
        await page.screenshot({ path: 'error.png' });
        console.log('Screenshot saved to error.png');
        process.exit(1);
    } finally {
        await browser.close();
    }
}

// Run if called directly
if (require.main === module) {
    refreshSession().catch(console.error);
}

module.exports = { refreshSession, extractSessionData, sendToApi };
