/*
 * Every price on this site is in Bahraini dinars.
 *
 * The Almotawaj Wallet gateway accepts BHD with up to three decimals and a
 * minimum of 0.100, so the same three-decimal form is shown everywhere instead
 * of a bare "$", which would misstate both the currency and the amount.
 */
const CURRENCY_LABEL = 'BHD';

function formatPrice(value) {
    const amount = Number(value);
    const safe = Number.isFinite(amount) ? amount : 0;
    return CURRENCY_LABEL + ' ' + safe.toFixed(3);
}
