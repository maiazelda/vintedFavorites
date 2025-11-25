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

    // Click on login button
    console.log('Clicking login button...');
    try {
        // Try multiple selectors for the login button
        const loginSelectors = [
            '[data-testid="header--login-button"]',
            'a[href*="login"]',
            'button:has-text("Se connecter")',
            'a:has-text("Se connecter")',
            '.Header__login'
        ];

        for (const selector of loginSelectors) {
            const loginBtn = await page.$(selector);
            if (loginBtn) {
                await loginBtn.click();
                break;
            }
        }

        await page.waitForTimeout(2000);
    } catch (e) {
        console.log('Could not find login button, trying direct URL...');
        await page.goto(`${config.vintedUrl}/member/login`, { waitUntil: 'networkidle' });
    }

    // Wait for login form to be fully loaded
    await page.waitForTimeout(3000);

    // Look for email/password fields
    console.log('Filling login form...');

    // Try to find and fill email field
    const emailSelectors = [
        'input[name="email"]',
        'input[type="email"]',
        'input[data-testid="email-input"]',
        'input[autocomplete="email"]',
        '#email',
        'input[placeholder*="mail"]',
        'input[placeholder*="Mail"]',
        'input[placeholder*="E-mail"]'
    ];

    let emailFilled = false;
    for (const selector of emailSelectors) {
        try {
            console.log(`Trying email selector: ${selector}`);
            const emailInput = await page.$(selector);
            if (emailInput) {
                const isVisible = await emailInput.isVisible().catch(() => false);
                if (isVisible) {
                    await emailInput.click();
                    await page.waitForTimeout(500);
                    await emailInput.fill(config.email);
                    emailFilled = true;
                    console.log(`Email filled using selector: ${selector}`);
                    break;
                }
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
            const passwordInput = await page.$(selector);
            if (passwordInput) {
                const isVisible = await passwordInput.isVisible().catch(() => false);
                if (isVisible) {
                    await passwordInput.click();
                    await page.waitForTimeout(500);
                    await passwordInput.fill(config.password);
                    passwordFilled = true;
                    console.log(`Password filled using selector: ${selector}`);
                    break;
                }
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
        'button:has-text("Se connecter")',
        'button:has-text("Connexion")',
        '[data-testid="login-submit"]'
    ];

    let submitClicked = false;
    for (const selector of submitSelectors) {
        try {
            const submitBtn = await page.$(selector);
            if (submitBtn) {
                // Wait for navigation after clicking submit
                await Promise.all([
                    page.waitForNavigation({ waitUntil: 'networkidle', timeout: 15000 }).catch(() => {}),
                    submitBtn.click()
                ]);
                submitClicked = true;
                console.log('Submit button clicked, waiting for navigation...');
                break;
            }
        } catch (e) {
            console.log('Error clicking submit button:', e.message);
        }
    }

    if (!submitClicked) {
        console.error('Could not find or click submit button');
        return false;
    }

    // Wait additional time for any redirects or page loads
    console.log('Waiting for login to complete...');
    await page.waitForTimeout(3000);

    // Check if login was successful - multiple strategies
    console.log('Checking login status...');

    // Strategy 1: Check if we're no longer on the login page
    const currentUrl = page.url();
    console.log('Current URL:', currentUrl);
    const isOnLoginPage = currentUrl.includes('/login') || currentUrl.includes('/member/login');

    if (isOnLoginPage) {
        console.log('Still on login page, checking for error messages or CAPTCHA...');

        // Check for CAPTCHA
        const hasCaptcha = await page.evaluate(() => {
            // Check for common CAPTCHA indicators
            const captchaSelectors = [
                'iframe[src*="recaptcha"]',
                'iframe[src*="captcha"]',
                '#captcha',
                '.captcha',
                '[class*="captcha"]',
                '[id*="captcha"]',
                '.g-recaptcha',
                '[class*="recaptcha"]'
            ];

            return captchaSelectors.some(selector => document.querySelector(selector) !== null);
        });

        if (hasCaptcha) {
            console.error('CAPTCHA detected! Vinted requires human verification.');
            console.error('This usually happens when:');
            console.error('  1. Too many automated login attempts');
            console.error('  2. Suspicious activity detected');
            console.error('Solutions:');
            console.error('  - Wait a few hours before trying again');
            console.error('  - Try logging in manually first in a normal browser');
            console.error('  - Check if your IP is flagged');
            await page.screenshot({ path: 'captcha-detected.png', fullPage: true });
            console.log('Screenshot saved to captcha-detected.png');
            return false;
        }

        // Check for other error messages
        const errorInfo = await page.evaluate(() => {
            const errorTexts = ['incorrect', 'invalid', 'erreur', 'mauvais', 'wrong'];
            const bodyText = document.body.innerText.toLowerCase();
            const hasError = errorTexts.some(text => bodyText.includes(text));

            // Try to find specific error message
            const errorElements = document.querySelectorAll('.error, [class*="error"], [class*="Error"]');
            const errorMessages = Array.from(errorElements).map(el => el.innerText).filter(text => text.length > 0);

            return {
                hasError,
                messages: errorMessages
            };
        });

        if (errorInfo.hasError) {
            console.error('Login error detected on page');
            if (errorInfo.messages.length > 0) {
                console.error('Error messages:', errorInfo.messages);
            }
            return false;
        }

        // Still on login page but no obvious errors - might be 2FA
        console.log('Still on login page without obvious errors. Checking for 2FA...');
        const has2FA = await page.evaluate(() => {
            const bodyText = document.body.innerText.toLowerCase();
            return bodyText.includes('2fa') ||
                   bodyText.includes('two-factor') ||
                   bodyText.includes('deux facteurs') ||
                   bodyText.includes('vérification') ||
                   bodyText.includes('verification') ||
                   bodyText.includes('code');
        });

        if (has2FA) {
            console.error('Two-factor authentication (2FA) detected!');
            console.error('This script cannot handle 2FA automatically.');
            console.error('Please disable 2FA on your Vinted account or complete it manually.');
            await page.screenshot({ path: '2fa-detected.png', fullPage: true });
            console.log('Screenshot saved to 2fa-detected.png');
            return false;
        }
    }

    // Strategy 2: Try to find user-specific elements
    const loginSuccess = await page.evaluate(() => {
        // Check for various indicators of being logged in
        const indicators = [
            '[data-testid="header--avatar"]',
            '.Header__user-avatar',
            '[class*="avatar"]',
            '[class*="Avatar"]',
            '[data-testid="user-menu"]',
            '.user-menu',
            'a[href*="/profile"]',
            'a[href*="/member/general"]'
        ];

        for (const selector of indicators) {
            if (document.querySelector(selector)) {
                console.log('Found login indicator:', selector);
                return true;
            }
        }

        // Also check if we're on the home page or any page other than login
        const isNotLoginPage = !window.location.pathname.includes('/login');
        return isNotLoginPage;
    });

    // Strategy 3: Try to navigate to favorites page as final verification
    if (loginSuccess || !isOnLoginPage) {
        console.log('Login appears successful, verifying by navigating to favorites...');
        try {
            await page.goto(`${config.vintedUrl}/member/items/favourites`, {
                waitUntil: 'domcontentloaded',
                timeout: 10000
            });
            await page.waitForTimeout(2000);

            // If we can access favorites page, we're definitely logged in
            const onFavoritesPage = page.url().includes('/favourites') || page.url().includes('/favorites');
            if (onFavoritesPage) {
                console.log('Login successful! Successfully accessed favorites page.');
                return true;
            }
        } catch (e) {
            console.log('Could not navigate to favorites, but login may still be successful');
        }

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
        // Login (this now includes navigation to favorites for verification)
        const loginSuccess = await login(page);

        if (!loginSuccess) {
            console.error('Login failed!');
            // Take a screenshot for debugging
            await page.screenshot({ path: 'login-failed.png', fullPage: true });
            console.log('Screenshot saved to login-failed.png');
            process.exit(1);
        }

        // Ensure we're on favorites page (login should have navigated there already)
        const currentUrl = page.url();
        if (!currentUrl.includes('/favourites') && !currentUrl.includes('/favorites')) {
            console.log('Navigating to favorites page to ensure all cookies are set...');
            await page.goto(`${config.vintedUrl}/member/items/favourites`, { waitUntil: 'networkidle' });
            await page.waitForTimeout(2000);
        } else {
            console.log('Already on favorites page');
            await page.waitForTimeout(1000);
        }

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
