const MAX_SOURCE_BYTES = 10 * 1024 * 1024;
const MAX_DIMENSION = 512;

/** Prepare an avatar for the profile API without sending an oversized camera image. */
export async function prepareAvatar(file: File): Promise<string> {
  if (!file.type.startsWith("image/")) throw new Error("请选择图片文件");
  if (file.size > MAX_SOURCE_BYTES) throw new Error("头像图片不能超过 10MB");

  const source = await readAsDataUrl(file);
  if (typeof document === "undefined") return source;

  try {
    const image = await loadImage(source);
    const longestSide = Math.max(image.naturalWidth || image.width, image.naturalHeight || image.height);
    if (!longestSide) return source;
    const scale = Math.min(1, MAX_DIMENSION / longestSide);
    const width = Math.max(1, Math.round((image.naturalWidth || image.width) * scale));
    const height = Math.max(1, Math.round((image.naturalHeight || image.height) * scale));
    const canvas = document.createElement("canvas");
    canvas.width = width;
    canvas.height = height;
    const context = canvas.getContext("2d");
    if (!context) return source;
    context.fillStyle = "#ffffff";
    context.fillRect(0, 0, width, height);
    context.drawImage(image, 0, 0, width, height);
    return canvas.toDataURL("image/jpeg", 0.82);
  } catch {
    // Keep a valid browser-readable image when a legacy browser cannot decode it.
    return source;
  }
}

function readAsDataUrl(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => typeof reader.result === "string" ? resolve(reader.result) : reject(new Error("头像读取失败"));
    reader.onerror = () => reject(new Error("头像读取失败，请重新选择图片"));
    reader.readAsDataURL(file);
  });
}

function loadImage(source: string): Promise<HTMLImageElement> {
  return new Promise((resolve, reject) => {
    const image = new Image();
    image.onload = () => resolve(image);
    image.onerror = () => reject(new Error("头像格式无法识别"));
    image.src = source;
  });
}
