/* Browser authentication uses HttpOnly cookies; JavaScript never reads the JWT. */
let csrfToken = null;

async function loadCsrfToken() {
    const response = await fetch('/auth/user/csrf', {
        credentials: 'include',
        cache: 'no-store'
    });
    if (!response.ok) throw new Error('Unable to initialize security token');
    const data = await response.json();
    csrfToken = data.token;
    return csrfToken;
}

async function authenticatedFetch(url, options = {}) {
    const request = {
        ...options,
        credentials: 'include',
        headers: { ...(options.headers || {}) }
    };
    const method = (request.method || 'GET').toUpperCase();
    const needsCsrf = !['GET', 'HEAD', 'OPTIONS'].includes(method);

    if (needsCsrf) {
        if (!csrfToken) await loadCsrfToken();
        request.headers['X-XSRF-TOKEN'] = csrfToken;
    }

    let response = await fetch(url, request);

    // The CSRF cookie may have rotated (another tab, login, logout). Refresh once and retry.
    if (needsCsrf && response.status === 403) {
        await loadCsrfToken();
        request.headers['X-XSRF-TOKEN'] = csrfToken;
        response = await fetch(url, request);
    }

    if (response.status === 401 && !url.startsWith('/auth/user/')) {
        window.dispatchEvent(new CustomEvent('auth-expired'));
    }
    return response;
}

async function getSession() {
    const response = await authenticatedFetch('/auth/user/session');
    return response.ok ? response.json() : null;
}

async function logout() {
    const response = await authenticatedFetch('/auth/user/logout', { method: 'POST' });
    csrfToken = null;
    return response;
}
