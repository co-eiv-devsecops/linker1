const form = document.getElementById('link-form');
const urlInput = document.getElementById('url');
const aliasInput = document.getElementById('alias');
const message = document.getElementById('message');
const result = document.getElementById('result');
const shortLink = document.getElementById('short-link');
const copyButton = document.getElementById('copy-btn');

function setMessage(text, isError = false) {
  message.textContent = text;
  message.style.color = isError ? '#fca5a5' : '#9fb2d1';
}

function setResult(shortUrl) {
  shortLink.textContent = shortUrl;
  shortLink.href = shortUrl;
  result.classList.remove('hidden');
}

form.addEventListener('submit', async (event) => {
  event.preventDefault();
  result.classList.add('hidden');
  setMessage('Creando enlace...');

  try {
    const alias = aliasInput.value.trim();
    const payload = { url: urlInput.value.trim() };
    if (alias) {
      payload.alias = alias;
    }

    const response = await fetch('/link', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(payload),
    });

    const text = await response.text();

    if (!response.ok) {
      throw new Error(text || 'No se pudo acortar la URL.');
    }

    const shortPath = response.headers.get('Location') || `/${text}`;
    const shortUrl = new URL(shortPath, window.location.origin).toString();

    setResult(shortUrl);
    setMessage('Enlace listo.');
  } catch (error) {
    setMessage(error.message, true);
  }
});

copyButton.addEventListener('click', async () => {
  const value = shortLink.href;
  if (!value) {
    return;
  }

  try {
    await navigator.clipboard.writeText(value);
    setMessage('Enlace copiado al portapapeles.');
  } catch (error) {
    setMessage('No se pudo copiar el enlace.', true);
  }
});