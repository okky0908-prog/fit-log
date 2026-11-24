import { describe, expect, test, vi } from 'vitest'
import { fitSize, ICON_MAX_SIDE, type ImageCodec, shrinkImage, UnusableImageError } from './shrink'

// 画像の縮小（docs/features.md「記録の項目」の画像：選べるのは1枚20MBまで、長い辺1600pxまで縮小して送る。サーバーは1枚2MBまで）

const MB = 1024 * 1024

function file(type: string, size = 1000): File {
  const f = new File(['x'], 'photo', { type })
  Object.defineProperty(f, 'size', { value: size })
  return f
}

/** 偽物の読み込み・書き出し。書き出した大きさは、画質ごとに決めておく */
function codec(width: number, height: number, sizeByQuality: Record<number, number> = {}) {
  const encode = vi.fn<ImageCodec['encode']>(async (_source, w, h, quality) => {
    const blob = new Blob(['y'], { type: 'image/jpeg' })
    Object.defineProperty(blob, 'size', { value: sizeByQuality[quality] ?? 100_000 })
    Object.defineProperty(blob, 'dimensions', { value: `${w}x${h}` })
    return blob
  })
  const decode = vi.fn<ImageCodec['decode']>(async () => ({
    width,
    height,
    source: {} as ImageBitmap,
  }))
  return { decode, encode }
}

describe('縮小したあとの大きさ', () => {
  test('長い辺を1600pxにそろえ、縦横の比は変えない', () => {
    expect(fitSize(4000, 3000)).toEqual({ width: 1600, height: 1200 })
    expect(fitSize(3000, 4000)).toEqual({ width: 1200, height: 1600 })
    expect(fitSize(3333, 1000)).toEqual({ width: 1600, height: 480 })
  })

  test('1600px 以下なら、そのまま（大きくしない）', () => {
    expect(fitSize(1600, 900)).toEqual({ width: 1600, height: 900 })
    expect(fitSize(800, 600)).toEqual({ width: 800, height: 600 })
  })

  test('端数は四捨五入し、1px より小さくしない', () => {
    expect(fitSize(1601, 1000)).toEqual({ width: 1600, height: 999 })
    expect(fitSize(10000, 1)).toEqual({ width: 1600, height: 1 })
    expect(fitSize(1, 10000)).toEqual({ width: 1, height: 1600 })
  })
})

describe('アイコン（512px 四方に収める）', () => {
  test('長い辺を512pxにそろえて書き出す', async () => {
    const fake = codec(4000, 3000)

    await shrinkImage(file('image/png'), fake, ICON_MAX_SIDE)

    expect(fake.encode).toHaveBeenCalledWith({}, 512, 384, 0.85)
    expect(fitSize(300, 200, ICON_MAX_SIDE)).toEqual({ width: 300, height: 200 })
  })
})

describe('縮小', () => {
  test('JPEG・PNG・WebP を、縮小した JPEG にする（小さな画像も書き出し直して、位置情報などの付属データを消す）', async () => {
    for (const type of ['image/jpeg', 'image/png', 'image/webp']) {
      const fake = codec(4000, 3000)

      const blob = await shrinkImage(file(type), fake)

      expect(blob.type).toBe('image/jpeg')
      expect(fake.encode).toHaveBeenCalledWith({}, 1600, 1200, 0.85)
    }
    const small = codec(800, 600)
    await shrinkImage(file('image/jpeg'), small)
    expect(small.encode).toHaveBeenCalledWith({}, 800, 600, 0.85)
  })

  test('2MB を超えたら、画質を下げて書き出し直す', async () => {
    const fake = codec(4000, 3000, { 0.85: 2 * MB + 1, 0.7: 2 * MB })

    const blob = await shrinkImage(file('image/jpeg'), fake)

    expect(blob.size).toBe(2 * MB)
    expect(fake.encode.mock.calls.map((call) => call[3])).toEqual([0.85, 0.7])
  })

  test('画質を一番下げても 2MB を超えるなら、使えない', async () => {
    const fake = codec(4000, 3000, { 0.85: 3 * MB, 0.7: 3 * MB, 0.5: 2 * MB + 1 })

    await expect(shrinkImage(file('image/jpeg'), fake)).rejects.toBeInstanceOf(UnusableImageError)
    expect(fake.encode).toHaveBeenCalledTimes(3)
  })

  test('JPEG・PNG・WebP 以外は、読み込まずに使えない', async () => {
    const fake = codec(100, 100)

    await expect(shrinkImage(file('image/gif'), fake)).rejects.toThrow(
      'この画像は使えません（JPEG・PNG・WebP、20MBまで）',
    )
    await expect(shrinkImage(file('image/heic'), fake)).rejects.toBeInstanceOf(UnusableImageError)
    expect(fake.decode).not.toHaveBeenCalled()
  })

  test('選べるのは 20MB ちょうどまで', async () => {
    await expect(shrinkImage(file('image/jpeg', 20 * MB), codec(100, 100))).resolves.toBeDefined()
    await expect(
      shrinkImage(file('image/jpeg', 20 * MB + 1), codec(100, 100)),
    ).rejects.toBeInstanceOf(UnusableImageError)
  })

  test('読み込めない（壊れた）画像・書き出せない画像は、使えない', async () => {
    const broken = codec(100, 100)
    broken.decode.mockRejectedValue(new Error('decode failed'))
    await expect(shrinkImage(file('image/png'), broken)).rejects.toBeInstanceOf(UnusableImageError)

    const unwritable = codec(100, 100)
    unwritable.encode.mockRejectedValue(new Error('toBlob failed'))
    await expect(shrinkImage(file('image/png'), unwritable)).rejects.toBeInstanceOf(
      UnusableImageError,
    )
  })
})
