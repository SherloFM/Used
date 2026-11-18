(function () {
    'use strict';

    const MAX_SOURCE_BYTES = 25 * 1024 * 1024;
    const TARGET_BYTES = 4.5 * 1024 * 1024;
    const MAX_DIMENSION = 2048;

    function extension(name) {
        const match = String(name || '').toLowerCase().match(/\.([a-z0-9]+)$/);
        return match ? match[1] : '';
    }

    function isImage(file) {
        const ext = extension(file && file.name);
        return !!file && ((file.type || '').startsWith('image/') ||
            ['jpg', 'jpeg', 'png', 'heic', 'heif'].includes(ext));
    }

    function outputName(name) {
        const base = String(name || 'mobile-photo').replace(/\.[^.]+$/, '') || 'mobile-photo';
        return base + '.jpg';
    }

    async function decode(file) {
        if ('createImageBitmap' in window) {
            try {
                return await createImageBitmap(file, { imageOrientation: 'from-image' });
            } catch (ignored) {
                // Safari can render some Photos-library formats through an img
                // element even when createImageBitmap does not support them.
            }
        }

        const url = URL.createObjectURL(file);
        try {
            const image = new Image();
            image.decoding = 'async';
            image.src = url;
            if (typeof image.decode === 'function') {
                await image.decode();
            } else {
                await new Promise((resolve, reject) => {
                    image.onload = resolve;
                    image.onerror = () => reject(new Error('Image decode failed'));
                });
            }
            return image;
        } finally {
            URL.revokeObjectURL(url);
        }
    }

    function jpeg(canvas, quality) {
        return new Promise((resolve, reject) => {
            canvas.toBlob(blob => blob ? resolve(blob) : reject(new Error('The photo could not be converted.')),
                'image/jpeg', quality);
        });
    }

    /** Resize, orient and convert a phone photo to a server-safe JPEG. */
    async function prepare(file) {
        if (!isImage(file)) throw new Error('Please choose an image from your camera or photo library.');
        if (file.size > MAX_SOURCE_BYTES) throw new Error('That photo is larger than 25 MB. Please choose a smaller photo.');

        let source;
        try {
            source = await decode(file);
        } catch (failure) {
            throw new Error('This phone photo format could not be read. On iPhone, choose Most Compatible in Camera settings or select a screenshot.');
        }

        try {
            const sourceWidth = source.width || source.naturalWidth;
            const sourceHeight = source.height || source.naturalHeight;
            if (!sourceWidth || !sourceHeight) throw new Error('The selected photo has no readable dimensions.');

            const scale = Math.min(1, MAX_DIMENSION / Math.max(sourceWidth, sourceHeight));
            const width = Math.max(1, Math.round(sourceWidth * scale));
            const height = Math.max(1, Math.round(sourceHeight * scale));
            const canvas = document.createElement('canvas');
            canvas.width = width;
            canvas.height = height;
            const context = canvas.getContext('2d', { alpha: false });
            if (!context) throw new Error('This browser cannot prepare the photo.');
            context.fillStyle = '#ffffff';
            context.fillRect(0, 0, width, height);
            context.drawImage(source, 0, 0, width, height);

            let quality = 0.88;
            let blob = await jpeg(canvas, quality);
            while (blob.size > TARGET_BYTES && quality > 0.55) {
                quality -= 0.08;
                blob = await jpeg(canvas, quality);
            }
            if (blob.size > TARGET_BYTES) throw new Error('The photo is still too large after resizing.');
            return new File([blob], outputName(file.name), { type: 'image/jpeg', lastModified: Date.now() });
        } finally {
            if (source && typeof source.close === 'function') source.close();
        }
    }

    window.MobileImage = { prepare };
})();
