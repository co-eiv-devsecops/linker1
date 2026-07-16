const fs = require('fs');
const path = require('path');

function fail(message) {
  throw new Error(message);
}

function shellSplit(command) {
  const parts = [];
  let part = '';
  let quote = '';
  let escaped = false;

  for (const char of command) {
    if (escaped) {
      part += char;
      escaped = false;
      continue;
    }

    if (char === '\\' && quote !== "'") {
      escaped = true;
      continue;
    }

    if ((char === "'" || char === '"') && !quote) {
      quote = char;
      continue;
    }

    if (char === quote) {
      quote = '';
      continue;
    }

    if (/\s/.test(char) && !quote) {
      if (part) {
        parts.push(part);
        part = '';
      }
      continue;
    }

    part += char;
  }

  if (escaped || quote) {
    fail('OCI SSH command contains unfinished quoting or escaping.');
  }
  if (part) {
    parts.push(part);
  }
  return parts;
}

function getProxyCommand(tokens) {
  for (let index = 0; index < tokens.length; index += 1) {
    const token = tokens[index];
    const option = token === '-o' ? tokens[index + 1] || '' : token.startsWith('-o') ? token.slice(2) : '';
    if (option.startsWith('ProxyCommand=')) {
      return option.slice('ProxyCommand='.length);
    }
  }
  fail('OCI SSH command did not include a ProxyCommand option.');
}

function getPort(tokens) {
  for (let index = 0; index < tokens.length; index += 1) {
    if (tokens[index] === '-p' && tokens[index + 1]) {
      return tokens[index + 1];
    }
    if (tokens[index].startsWith('-p') && tokens[index].length > 2) {
      return tokens[index].slice(2);
    }
  }
  return '22';
}

function shellQuote(value) {
  return /^[A-Za-z0-9_./:@%+=,-]+$/.test(value)
    ? value
    : `'${value.replace(/'/g, `'"'"'`)}'`;
}

function makeProxyCommand(proxyCommand, privateKey, knownHosts) {
  const tokens = shellSplit(proxyCommand.replaceAll('<privateKey>', privateKey));
  if (tokens[0] !== 'ssh') {
    fail('OCI ProxyCommand was not an ssh command.');
  }

  tokens.splice(
    1,
    0,
    '-o',
    'StrictHostKeyChecking=accept-new',
    '-o',
    `UserKnownHostsFile=${knownHosts}`,
  );
  return tokens.map(shellQuote).join(' ');
}

module.exports = async ({ core }) => {
  const sessionId = (process.env.SESSION_ID || '').trim();
  const commandFile = process.env.SSH_COMMAND_FILE || '';
  const privateKey = path.resolve(process.env.PRIVATE_KEY_FILE || '');
  const workDir = path.resolve(process.env.WORK_DIR || '');

  if (!sessionId) fail('SESSION_ID was not provided.');
  if (!fs.existsSync(commandFile)) fail(`SSH command file does not exist: ${commandFile}`);
  if (!fs.existsSync(privateKey)) fail(`Private key file does not exist: ${privateKey}`);
  if (!fs.existsSync(workDir)) fail(`Work directory does not exist: ${workDir}`);

  const command = fs.readFileSync(commandFile, 'utf8').trim();
  const tokens = shellSplit(command);
  if (tokens[0] !== 'ssh') fail('OCI ssh-metadata command did not start with ssh.');

  const target = tokens[tokens.length - 1];
  if (!target || target.startsWith('-') || !target.includes('@')) {
    fail('Could not parse target user and host from OCI SSH command.');
  }

  const at = target.lastIndexOf('@');
  const user = target.slice(0, at);
  const host = target.slice(at + 1);
  if (!user || !host) {
    fail('Could not parse target user and host from OCI SSH command.');
  }

  const knownHosts = path.join(workDir, 'known_hosts');
  const sshConfig = path.join(workDir, 'ssh_config');
  const proxyCommand = makeProxyCommand(getProxyCommand(tokens), privateKey, knownHosts);

  fs.writeFileSync(
    sshConfig,
    `Host oci-bastion-target
  HostName ${host}
  User ${user}
  Port ${getPort(tokens)}
  IdentityFile ${privateKey}
  IdentitiesOnly yes
  StrictHostKeyChecking accept-new
  UserKnownHostsFile ${knownHosts}
  ServerAliveInterval 30
  ProxyCommand ${proxyCommand}
`,
    { mode: 0o600 },
  );
  fs.chmodSync(sshConfig, 0o600);

  core.setOutput('session-id', sessionId);
  core.setOutput('ssh-config', sshConfig);
};
