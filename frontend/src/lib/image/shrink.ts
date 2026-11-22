/*
 * 記録の画像を、送る前にブラウザで縮小する（docs/features.md「記録の項目」の画像）。
 * - 選べるのは JPEG・PNG・WebP で、1枚20MBまで。長い辺を1600pxまでにして、JPEG で書き出す
 * - 小さな画像も必ず Canvas で書き出し直す。撮影した場所（EXIF の GPS）などの付属データが消える
 *   （サーバーは位置情報を含む画像を受け付けない。docs/api.md A-20）
 * - サーバーが受け付けるのは1枚2MBまでなので、超えたら画質を下げて書き出し直す
 */

/** 選べる大きさの上限（20MB） */
export const MAX_SOURCE_BYTES = 20 * 1024 * 1024
/** 送れる大きさの上限（2MB。サーバーの上限） */
export const MAX_UPLOAD_BYTES = 2 * 1024 * 1024
/** 縮小したあとの長い辺 */
export const MAX_SIDE = 1600

export const UNUSABLE_IMAGE_MESSAGE = 'この画像は使えません（JPEG・PNG・WebP、20MBまで）'

const ACCEPTED_TYPES = new Set(['image/jpeg', 'image/png', 'image/webp'])
/** 書き出しの画質。2MB を超えたら、次の画質でやり直す */
const QUALITIES = [0.85, 0.7, 0.5]

/** 画像の読み込みと書き出し（テストでは偽物にする） */
export interface ImageCodec {
  decode(file: Blob): Promise<{ width: number; height: number; source: CanvasImageSource }>
  encode(source: CanvasImageSource, width: number, height: number, quality: number): Promise<Blob>
}

/** 選べない・縮小できない画像 */
export class UnusableImageError extends Error {
  constructor() {
    super(UNUSABLE_IMAGE_MESSAGE)
    this.name = 'UnusableImageError'
  }
}

/** 長い辺を MAX_SIDE にそろえた大きさ。縦横の比は変えず、小さな画像は大きくしない */
export function fitSize(width: number, height: number): { width: number; height: number } {
  const scale = Math.min(1, MAX_SIDE / Math.max(width, height))
  return {
    width: Math.max(1, Math.round(width * scale)),
    height: Math.max(1, Math.round(height * scale)),
  }
}

/** 縮小して JPEG にする。使えない画像なら UnusableImageError */
export async function shrinkImage(file: File, codec: ImageCodec = browserCodec): Promise<Blob> {
  if (!ACCEPTED_TYPES.has(file.type) || file.size > MAX_SOURCE_BYTES) {
    throw new UnusableImageError()
  }
  try {
    const image = await codec.decode(file)
    const size = fitSize(image.width, image.height)
    for (const quality of QUALITIES) {
      const blob = await codec.encode(image.source, size.width, size.height, quality)
      if (blob.size <= MAX_UPLOAD_BYTES) {
        return blob
      }
    }
  } catch {
    // 読み込めない（壊れた）・書き出せない画像。下で「使えない」にする
  }
  throw new UnusableImageError()
}

/** ブラウザの読み込み（EXIF の向きは createImageBitmap が反映する）と、Canvas での書き出し */
const browserCodec: ImageCodec = {
  async decode(file) {
    const bitmap = await createImageBitmap(file)
    return { width: bitmap.width, height: bitmap.height, source: bitmap }
  },
  encode(source, width, height, quality) {
    const canvas = document.createElement('canvas')
    canvas.width = width
    canvas.height = height
    const context = canvas.getContext('2d') as CanvasRenderingContext2D
    // PNG の透明な部分が黒くならないよう、白で塗ってから描く
    context.fillStyle = '#ffffff'
    context.fillRect(0, 0, width, height)
    context.drawImage(source, 0, 0, width, height)
    // 書き出せなければ null が渡り、呼ぶ側で blob.size を読むときに失敗して「使えない」になる
    return new Promise((resolve) =>
      canvas.toBlob((blob) => resolve(blob as Blob), 'image/jpeg', quality),
    )
  },
}
