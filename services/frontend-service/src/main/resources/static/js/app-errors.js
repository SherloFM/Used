(function () {
    function friendlyMessage(status, body) {
        if (body && typeof body.message === 'string' && body.message.trim()) {
            var msg = body.message.trim();
            if (status === 401) {
                if (/session|login|token|expired|auth/i.test(msg)) {
                    return 'Your session has expired. Please log in again.';
                }
            }
            if (status === 403) {
                if (/permission|forbid/i.test(msg)) {
                    return 'You do not have permission to do that.';
                }
            }
            return msg;
        }
        switch (status) {
            case 400: return 'Some of the information sent was not valid. Please check it and try again.';
            case 401: return 'Your session has expired. Please log in again.';
            case 403: return 'You do not have permission to do that.';
            case 404: return 'We could not find what you were looking for.';
            case 409: return 'This item was updated by someone else. Please try again.';
            case 429: return 'Too many requests. Please wait a moment and try again.';
            case 500:
            case 502:
            case 503:
            case 504: return 'Something went wrong on our side. Please try again.';
            default:  return 'Something went wrong. Please try again.';
        }
    }

    function paymentReason(reason) {
        if (!reason) return null;
        var r = String(reason).toUpperCase();
        switch (r) {
            case 'ALREADY_SOLD': return 'This item has already been sold.';
            case 'INSUFFICIENT_FUNDS': return 'You do not have enough funds in your wallet.';
            case 'WALLET_MISSING': return 'Your wallet could not be found. Please try again.';
            case 'PAYMENT_NOT_FOUND': return 'We could not find this payment.';
            case 'QUEUE_FULL': return 'The marketplace is busy. Please try again in a moment.';
            case 'SYSTEM_ERROR': return 'We could not complete your payment. Please try again.';
            case 'COD_DECLINED': return 'The seller declined your cash-on-delivery request.';
            case 'ALREADY_HANDLED': return 'This request has already been handled.';
            case 'CARD_NOT_AVAILABLE':
            case 'CARD_NOT_SUPPORTED': return 'Card payments are coming soon. Please use your wallet or cash on delivery.';
            default: return 'Payment failed. Please try again.';
        }
    }

    function fromResponse(res, body) {
        return friendlyMessage(res.status, body);
    }

    function showToast(msg, isError) {
        var toast = document.getElementById('toast');
        if (!toast) {
            toast = document.createElement('div');
            toast.id = 'toast';
            document.body.appendChild(toast);
        }
        toast.textContent = msg;
        toast.style.background = isError ? '#e74c3c' : '#2ecc71';
        toast.classList.remove('show');
        void toast.offsetWidth;
        toast.classList.add('show');
        setTimeout(function () { toast.classList.remove('show'); }, 3000);
    }

    window.AppErrors = {
        friendlyMessage: friendlyMessage,
        paymentReason: paymentReason,
        fromResponse: fromResponse,
        showToast: showToast
    };
})();
