/* Actionable "cash on delivery" ping for sellers.
   Opens its own SSE connection and shows a floating card with
   Accept / Decline for every incoming COD request. */
(function () {
    const token = localStorage.getItem('used_jwt_token');
    if (!token) return;

    let container = null;

    function ensureContainer() {
        if (!container) {
            container = document.createElement('div');
            container.id = 'codPingContainer';
            container.style.cssText =
                'position:fixed;right:20px;bottom:100px;z-index:10000;' +
                'display:flex;flex-direction:column;gap:10px;max-width:380px;';
            document.body.appendChild(container);
        }
        return container;
    }

    function showRequest(data) {
        if (document.getElementById('cod-' + data.paymentId)) return;

        const el = document.createElement('div');
        el.id = 'cod-' + data.paymentId;
        el.style.cssText =
            'background:#fff;border-radius:12px;box-shadow:0 8px 30px rgba(0,0,0,0.18);' +
            'padding:14px;border-left:5px solid #a06cd5;font-family:"Segoe UI",sans-serif;';

        el.innerHTML = `
            <div style="display:flex;gap:10px;align-items:center;margin-bottom:10px;">
                ${data.imgUrl ? `<img src="${esc(data.imgUrl)}" alt=""
                    style="width:48px;height:48px;border-radius:8px;object-fit:cover;">` : ''}
                <div>
                    <div style="font-weight:700;color:#2c3e50;font-size:0.95rem;">
                        Cash on delivery request</div>
                    <div style="color:#666;font-size:0.85rem;">
                        ${esc(data.buyerName)} wants "${esc(data.listingTitle)}"</div>
                    <div style="color:#27ae60;font-weight:700;font-size:0.9rem;">
                        $${Number(data.amount).toFixed(2)}</div>
                </div>
            </div>
            <div style="display:flex;gap:8px;">
                <button data-action="accept" style="flex:1;padding:8px;border:none;border-radius:8px;
                    background:#27ae60;color:#fff;font-weight:600;cursor:pointer;">Accept</button>
                <button data-action="decline" style="flex:1;padding:8px;border:none;border-radius:8px;
                    background:#eee;color:#555;font-weight:600;cursor:pointer;">Decline</button>
            </div>
            <div class="cod-msg" style="font-size:0.8rem;margin-top:8px;color:#888;"></div>
        `;

        el.querySelector('[data-action="accept"]').onclick = () => respond(el, data.paymentId, 'accept');
        el.querySelector('[data-action="decline"]').onclick = () => respond(el, data.paymentId, 'decline');
        ensureContainer().appendChild(el);
    }

    async function respond(el, paymentId, action) {
        const msg = el.querySelector('.cod-msg');
        const buttons = el.querySelectorAll('button');
        buttons.forEach(b => b.disabled = true);
        msg.style.color = '#888';
        msg.textContent = action === 'accept' ? 'Accepting...' : 'Declining...';

        try {
            const res = await fetch(`/api/payments/${paymentId}/cod/${action}`, {
                method: 'POST',
                headers: { 'Authorization': 'Bearer ' + token }
            });
            const body = await res.json().catch(() => ({}));

            if (!res.ok) {
                msg.style.color = '#c0392b';
                msg.textContent = window.AppErrors
                    ? AppErrors.fromResponse(res, body)
                    : (body.message || 'Something went wrong. Please try again.');
                buttons.forEach(b => b.disabled = false);
                return;
            }

            el.style.borderLeftColor = action === 'accept' ? '#27ae60' : '#e74c3c';
            msg.style.color = action === 'accept' ? '#27ae60' : '#c0392b';
            msg.textContent = action === 'accept'
                ? 'Accepted - the buyer has been notified.'
                : 'Declined - the buyer has been notified.';

            document.dispatchEvent(new CustomEvent('cod:handled', {
                detail: { paymentId: paymentId, accepted: action === 'accept' }
            }));
            setTimeout(() => el.remove(), 4000);

        } catch (err) {
            msg.style.color = '#c0392b';
            msg.textContent = 'Could not reach the server. Please try again.';
            buttons.forEach(b => b.disabled = false);
        }
    }

    function esc(text) {
        const div = document.createElement('div');
        div.textContent = (text === null || text === undefined) ? '' : text;
        return div.innerHTML;
    }

    const source = new EventSource(`/api/notifications/stream?token=${encodeURIComponent(token)}`);
    source.addEventListener('COD_REQUEST', (e) => {
        try { showRequest(JSON.parse(e.data)); } catch (err) { console.error(err); }
    });
    source.onerror = () => { /* EventSource reconnects automatically */ };
})();
