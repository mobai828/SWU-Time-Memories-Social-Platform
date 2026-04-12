const DEFAULT_BACKGROUND = "https://images.unsplash.com/photo-1495344517868-8ebaf0a2044a?q=80&w=2560&auto=format&fit=crop";

const isValidUrl = (value) => {
    try {
        const parsed = new URL(value, window.location.origin);
        return parsed.protocol === "http:" || parsed.protocol === "https:";
    } catch {
        return false;
    }
};

const preload = (url) => new Promise((resolve) => {
    const image = new Image();
    image.onload = () => resolve(true);
    image.onerror = () => resolve(false);
    image.src = url;
    if (image.complete) {
        resolve(true);
    }
});

const sessionSelections = new Map();

const pickBackground = (config, storageKey) => {
    if (sessionSelections.has(storageKey)) {
        return sessionSelections.get(storageKey);
    }

    const images = Array.isArray(config?.images) ? config.images.filter(isValidUrl) : [];
    const fallbackImages = images.length > 0 ? images : [DEFAULT_BACKGROUND];
    const selectedImage = fallbackImages.includes(config?.selectedImage)
        ? config.selectedImage
        : fallbackImages[0];

    if (!config?.randomEnabled || fallbackImages.length <= 1) {
        localStorage.setItem(storageKey, selectedImage);
        sessionSelections.set(storageKey, selectedImage);
        return selectedImage;
    }

    const previousBackground = localStorage.getItem(storageKey);
    const candidates = fallbackImages.filter((image) => image !== previousBackground);
    const pool = candidates.length > 0 ? candidates : fallbackImages;
    const nextBackground = pool[Math.floor(Math.random() * pool.length)];
    localStorage.setItem(storageKey, nextBackground);
    sessionSelections.set(storageKey, nextBackground);
    return nextBackground;
};

document.addEventListener("DOMContentLoaded", async () => {
    const backgroundLayers = Array.from(document.querySelectorAll("[data-auth-background]"));
    if (backgroundLayers.length === 0) {
        return;
    }

    try {
        const response = await fetch("/api/public/login-background-config", {
            headers: {
                "Accept": "application/json"
            }
        });
        const config = response.ok ? await response.json() : null;

        for (const backgroundLayer of backgroundLayers) {
            const storageKey = backgroundLayer.dataset.storageKey || "java:last-login-background";
            const backgroundUrl = pickBackground(config, storageKey);
            const loaded = await preload(backgroundUrl);
            const finalUrl = loaded ? backgroundUrl : DEFAULT_BACKGROUND;
            backgroundLayer.style.backgroundImage = `url("${finalUrl}")`;
            requestAnimationFrame(() => backgroundLayer.classList.add("ready"));
        }
    } catch {
        for (const backgroundLayer of backgroundLayers) {
            backgroundLayer.style.backgroundImage = `url("${DEFAULT_BACKGROUND}")`;
            requestAnimationFrame(() => backgroundLayer.classList.add("ready"));
        }
    }
});
