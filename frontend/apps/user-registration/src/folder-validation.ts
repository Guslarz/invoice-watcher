import {Folder} from './registration.models';

export function folderError(
  folders: Folder[],
  folder: Folder,
  index: number,
): string {
  if (!folder.name.trim()) return 'Enter a folder name.';
  if (folder.name.length > 255) return 'Use at most 255 characters.';
  return folders.some((f, i) => i !== index && f.name === folder.name)
    ? 'Folder names must be unique.'
    : '';
}

export function hasInvalidFolders(folders: Folder[]): boolean {
  return (
    !folders.length ||
    folders.some(
      (folder, index) =>
        !!folderError(folders, folder, index) || folder.dateValid === false,
    )
  );
}
