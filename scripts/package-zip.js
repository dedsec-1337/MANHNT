import fs from 'fs';
import path from 'path';
import JSZip from 'jszip';

const rootDir = process.cwd();
const androidDir = path.join(rootDir, 'android');
const publicDir = path.join(rootDir, 'public');

if (!fs.existsSync(publicDir)) {
  fs.mkdirSync(publicDir, { recursive: true });
}

const zip = new JSZip();

function addFilesRecursively(dir, zipFolder) {
  const entries = fs.readdirSync(dir, { withFileTypes: true });
  for (const entry of entries) {
    const fullPath = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      const subFolder = zipFolder.folder(entry.name);
      addFilesRecursively(fullPath, subFolder);
    } else {
      const fileData = fs.readFileSync(fullPath);
      zipFolder.file(entry.name, fileData);
    }
  }
}

console.log('Archiving android folder to ZIP...');
const manhuntFolder = zip.folder('Manhunt');
addFilesRecursively(androidDir, manhuntFolder);

zip.generateAsync({ type: 'nodebuffer', compression: 'DEFLATE' }).then((content) => {
  const publicZipPath = path.join(publicDir, 'manhunt-android-app.zip');
  const rootZipPath = path.join(rootDir, 'manhunt-android-app.zip');

  fs.writeFileSync(publicZipPath, content);
  fs.writeFileSync(rootZipPath, content);

  console.log(`Successfully generated zip at:\n- ${publicZipPath} (${(content.length / 1024).toFixed(1)} KB)\n- ${rootZipPath}`);
});
