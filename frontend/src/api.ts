export async function api<T>(path: string, options: RequestInit = {}): Promise<T> {
  const token = localStorage.getItem("lumalife-token");
  const response = await fetch(path, {
    ...options,
    headers: {
      "Content-Type": "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...options.headers
    }
  });
  const json = await response.json();
  if (json.code !== 200) throw new Error(json.message);
  return json.data;
}
