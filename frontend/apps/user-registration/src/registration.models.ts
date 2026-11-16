export interface Folder {
  id?: string;
  name: string;
  startDate: string;
  dateValid?: boolean;
}
export interface Configuration {
  email: string;
  version: number;
  folders: Folder[];
  connected: boolean;
  reconnectionRequired: boolean;
}
export interface Session {
  email?: string;
  authenticated: boolean;
}
export interface RevocationResult {
  revoked: boolean;
}
