const form = document.getElementById('link-form');
const urlInput = document.getElementById('url');
const aliasInput = document.getElementById('alias');
const message = document.getElementById('message');
const result = document.getElementById('result');
const shortLink = document.getElementById('short-link');
const copyButton = document.getElementById('copy-btn');
const deleteButton = document.getElementById('delete-btn');
const statsButton = document.getElementById('stats-btn');
const statsResult = document.getElementById('stats-result');

let currentLinkId = null;

function setMessage(text, isError = false) {
  message.textContent = text;
  message.classList.toggle('message-error', isError);
  message.setAttribute('role', isError ? 'alert' : 'status');
}

function setResult(shortUrl, linkId) {
  shortLink.textContent = shortUrl;
  shortLink.href = shortUrl;
  currentLinkId = linkId;
  result.classList.remove('hidden');
  statsResult.textContent = '';
}

function creationError(response, responseMessage) {
  if (response.status === 409) {
    return 'El alias no está disponible. Elige otro.';
  }
  if (response.status === 400) {
    if (responseMessage.startsWith('La URL')) return responseMessage;
    if (responseMessage.startsWith('El alias')) return responseMessage;
    return 'Revisa la URL y el alias. Se aceptan direcciones web HTTP o HTTPS.';
  }
  return 'No fue posible crear el enlace. Inténtalo más tarde.';
}

form.addEventListener('submit', async (event) => {
  event.preventDefault();
  result.classList.add('hidden');
  statsResult.textContent = '';
  setMessage('Creando el enlace…');

  try {
    const alias = aliasInput.value.trim();
    const payload = { url: urlInput.value.trim() };
    if (alias) payload.alias = alias;

    const response = await fetch('/link', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    });
    const responseMessage = await response.text();

    if (!response.ok) {
      throw new Error(creationError(response, responseMessage));
    }

    const shortPath = response.headers.get('Location') || `/${responseMessage}`;
    const shortUrl = new URL(shortPath, window.location.origin).toString();
    const linkId = shortPath.startsWith('/') ? shortPath.slice(1) : shortPath;

    setResult(shortUrl, linkId);
    setMessage('Enlace listo para compartir.');
  } catch (error) {
    setMessage(error instanceof TypeError
      ? 'No se pudo conectar con Linker1. Inténtalo de nuevo.'
      : error.message, true);
  }
});

// navigator.clipboard only exists in secure contexts (HTTPS or localhost). On a plain
// HTTP demo deployment it is undefined, so fall back to the legacy copy command.
async function copyText(text) {
  if (navigator.clipboard && window.isSecureContext) {
    await navigator.clipboard.writeText(text);
    return;
  }
  const area = document.createElement('textarea');
  area.value = text;
  area.setAttribute('readonly', '');
  area.style.position = 'fixed';
  area.style.opacity = '0';
  document.body.appendChild(area);
  area.select();
  const copied = document.execCommand('copy');
  document.body.removeChild(area);
  if (!copied) throw new Error('copy failed');
}

copyButton.addEventListener('click', async () => {
  const value = shortLink.href;
  if (!value) return;

  try {
    await copyText(value);
    setMessage('Enlace copiado al portapapeles.');
  } catch (error) {
    setMessage('No se pudo copiar el enlace. Selecciónalo y cópialo manualmente.', true);
  }
});

deleteButton.addEventListener('click', async () => {
  if (!currentLinkId) return;
  setMessage('Eliminando el enlace…');

  try {
    const response = await fetch(`/${currentLinkId}`, { method: 'DELETE' });
    statsResult.textContent = '';
    if (!response.ok && response.status !== 404) {
      throw new Error('No se pudo eliminar el enlace. Inténtalo de nuevo.');
    }

    result.classList.add('hidden');
    currentLinkId = null;
    setMessage('Enlace eliminado.');
  } catch (error) {
    setMessage(error.message, true);
  }
});

statsButton.addEventListener('click', async () => {
  if (!currentLinkId) {
    return;
  }

  statsResult.textContent = 'Consultando aperturas...';

  try {
    const response = await fetch(`/link/${encodeURIComponent(currentLinkId)}/stats`);

    if (response.status === 404) {
      statsResult.textContent = 'Este enlace ya no existe, así que no hay aperturas para mostrar.';
      return;
    }
    if (!response.ok) {
      throw new Error('stats');
    }

    const data = await response.json();
    statsResult.textContent = data.clicks === 1
        ? 'Tu enlace se ha abierto 1 vez.'
        : `Tu enlace se ha abierto ${data.clicks} veces.`;
  } catch (error) {
    statsResult.textContent = 'No pudimos consultar las aperturas. Inténtalo de nuevo.';
  }
});
