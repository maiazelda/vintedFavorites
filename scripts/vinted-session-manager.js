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

    // Go directly to the login page
    await page.goto(`${config.vintedUrl}/auth/login`, { waitUntil: 'networkidle' });
    await page.waitForTimeout(3000);

    // Handle cookie consent if present
    try {
        const acceptSelectors = [
            '[data-testid="cookie-consent-accept-all"]',
            '#onetrust-accept-btn-handler',
            'button:has-text("Accepter")',
            'button:has-text("Tout accepter")',
            '[id*="accept"]',
            '[class*="accept"]'
        ];

        for (const selector of acceptSelectors) {
            try {
                const acceptButton = await page.$(selector);
                if (acceptButton && await acceptButton.isVisible()) {
                    console.log('Accepting cookies consent...');
                    await acceptButton.click();
                    await page.waitForTimeout(1000);
                    break;
                }
            } catch (e) {}
        }
    } catch (e) {
        // No consent dialog, continue
    }

    // Check if already logged in
    const isLoggedIn = await page.evaluate(() => {
        return window.location.pathname.includes('/member') ||
               !!document.querySelector('[data-testid*="avatar"], [class*="avatar"], [class*="user-menu"]');
    });

    if (isLoggedIn) {
        console.log('Already logged in!');
        return true;
    }

    // Log the current URL and page state for debugging
    console.log('Current URL:', page.url());

    // Take a screenshot to see the current page state
    await page.screenshot({ path: 'login-page-state.png', fullPage: true });
    console.log('Screenshot saved to login-page-state.png');

    // Wait for the page to fully load
    await page.waitForTimeout(2000);

    // Try to find email input with multiple strategies
    console.log('Looking for email input field...');

    // Strategy 1: Try to find all visible inputs and identify email field
    const allInputs = await page.evaluate(() => {
        const inputs = document.querySelectorAll('input');
        return Array.from(inputs).map(input => ({
            id: input.id,
            name: input.name,
            type: input.type,
            placeholder: input.placeholder,
            className: input.className,
            visible: input.offsetParent !== null,
            autocomplete: input.autocomplete
        }));
    });
    console.log('Found inputs:', JSON.stringify(allInputs, null, 2));

    // Try comprehensive email selectors
    const emailSelectors = [
        'input[name="email"]',
        'input[type="email"]',
        'input[id*="email"]',
        'input[data-testid*="email"]',
        'input[autocomplete="email"]',
        'input[autocomplete="username"]',
        'input[placeholder*="mail"]',
        'input[placeholder*="Mail"]',
        'input[placeholder*="adresse"]',
        'input[placeholder*="Adresse"]',
        'input[aria-label*="email"]',
        'input[aria-label*="mail"]',
        // Generic first visible text input
        'form input[type="text"]:first-of-type',
        'form input:not([type="password"]):not([type="hidden"]):first-of-type'
    ];

    let emailFilled = false;
    for (const selector of emailSelectors) {
        try {
            const emailInput = await page.$(selector);
            if (emailInput) {
                const isVisible = await emailInput.isVisible();
                if (isVisible) {
                    console.log(`Found email field with selector: ${selector}`);
                    await emailInput.click();
                    await page.waitForTimeout(300);
                    await emailInput.fill(config.email);
                    emailFilled = true;
                    console.log('Email filled successfully');
                    break;
                }
            }
        } catch (e) {
            // Continue to next selector
        }
    }

    if (!emailFilled) {
        console.error('Could not find email input field with standard selectors');

        // Last resort: try to type into any focused element or first input
        try {
            console.log('Trying keyboard-based approach...');
            await page.keyboard.press('Tab');
            await page.waitForTimeout(500);
            await page.keyboard.type(config.email, { delay: 50 });
            emailFilled = true;
            console.log('Email typed via keyboard');
        } catch (e) {
            console.error('Keyboard approach also failed');
            await page.screenshot({ path: 'email-field-not-found.png', fullPage: true });
            return false;
        }
    }

    // Wait before password
    await page.waitForTimeout(500);

    // Try to find and fill password field
    const passwordSelectors = [
        'input[type="password"]',
        'input[name="password"]',
        'input[id*="password"]',
        'input[data-testid*="password"]',
        'input[autocomplete="current-password"]',
        'input[placeholder*="mot de passe"]',
        'input[placeholder*="Mot de passe"]',
        'input[placeholder*="password"]'
    ];

    let passwordFilled = false;
    for (const selector of passwordSelectors) {
        try {
            const passwordInput = await page.$(selector);
            if (passwordInput) {
                const isVisible = await passwordInput.isVisible();
                if (isVisible) {
                    console.log(`Found password field with selector: ${selector}`);
                    await passwordInput.click();
                    await page.waitForTimeout(300);
                    await passwordInput.fill(config.password);
                    passwordFilled = true;
                    console.log('Password filled successfully');
                    break;
                }
            }
        } catch (e) {
            // Continue to next selector
        }
    }

    if (!passwordFilled) {
        console.error('Could not find password input field');

        // Try keyboard approach
        try {
            console.log('Trying keyboard-based approach for password...');
            await page.keyboard.press('Tab');
            await page.waitForTimeout(500);
            await page.keyboard.type(config.password, { delay: 50 });
            passwordFilled = true;
            console.log('Password typed via keyboard');
        } catch (e) {
            console.error('Keyboard approach for password also failed');
            await page.screenshot({ path: 'password-field-not-found.png', fullPage: true });
            return false;
        }
    }

    // Submit the form
    console.log('Submitting login form...');

    const submitSelectors = [
        'button[type="submit"]',
        'form button:not([type="button"])',
        'button:has-text("Se connecter")',
        'button:has-text("Connexion")',
        'button:has-text("Continuer")',
        '[data-testid*="submit"]',
        '[data-testid*="login"]'
    ];

    let submitted = false;
    for (const selector of submitSelectors) {
        try {
            const submitBtn = await page.$(selector);
            if (submitBtn && await submitBtn.isVisible()) {
                console.log(`Clicking submit button with selector: ${selector}`);
                await submitBtn.click();
                submitted = true;
                break;
            }
        } catch (e) {}
    }

    if (!submitted) {
        // Try pressing Enter
        console.log('Trying Enter key to submit...');
        await page.keyboard.press('Enter');
    }

    // Wait for login to complete
    console.log('Waiting for login to complete...');
    await page.waitForTimeout(5000);

    // Check current URL
    const currentUrl = page.url();
    console.log('URL after login attempt:', currentUrl);

    // Check if login was successful
    const loginSuccess = await page.evaluate(() => {
        // Check various indicators of being logged in
        const hasAvatar = !!document.querySelector('[data-testid*="avatar"], [class*="avatar"], [class*="user-menu"]');
        const isOnMemberPage = window.location.pathname.includes('/member');
        const hasLogoutOption = !!document.querySelector('[data-testid*="logout"], [class*="logout"]');
        const notOnLoginPage = !window.location.pathname.includes('/login') && !window.location.pathname.includes('/auth');

        return hasAvatar || isOnMemberPage || hasLogoutOption || notOnLoginPage;
    });

    if (loginSuccess) {
        console.log('Login successful!');
        return true;
    } else {
        console.error('Login may have failed. Taking screenshot...');
        await page.screenshot({ path: 'login-result.png', fullPage: true });
        console.log('Screenshot saved to login-result.png');

        // Check for error messages
        const errorMessage = await page.evaluate(() => {
            const errorEl = document.querySelector('[class*="error"], [class*="alert"], [role="alert"]');
            return errorEl ? errorEl.textContent : null;
        });

        if (errorMessage) {
            console.log('Error message on page:', errorMessage);
        }

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
