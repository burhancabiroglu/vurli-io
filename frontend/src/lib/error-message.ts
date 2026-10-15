/**
 * The translated text for an API error key such as "weak_password.TOO_SHORT", falling
 * back to the general code and then to a generic message.
 */
export function errorMessage(errors: Record<string, string>, key: string, unexpected: string): string {
  return errors[key] ?? errors[key.split(".")[0]] ?? unexpected;
}
