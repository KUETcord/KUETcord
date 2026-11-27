export type YearTermCode = '1-1' | '1-2' | '2-1' | '2-2' | '3-1' | '3-2' | '4-1' | '4-2';

export const YEAR_TERM_OPTIONS: ReadonlyArray<{ code: YearTermCode; label: string; shortLabel: string }> = [
  { code: '1-1', label: '1st Year, 1st Term (1-1)', shortLabel: '1-1' },
  { code: '1-2', label: '1st Year, 2nd Term (1-2)', shortLabel: '1-2' },
  { code: '2-1', label: '2nd Year, 1st Term (2-1)', shortLabel: '2-1' },
  { code: '2-2', label: '2nd Year, 2nd Term (2-2)', shortLabel: '2-2' },
  { code: '3-1', label: '3rd Year, 1st Term (3-1)', shortLabel: '3-1' },
  { code: '3-2', label: '3rd Year, 2nd Term (3-2)', shortLabel: '3-2' },
  { code: '4-1', label: '4th Year, 1st Term (4-1)', shortLabel: '4-1' },
  { code: '4-2', label: '4th Year, 2nd Term (4-2)', shortLabel: '4-2' },
];

export const KUET_DEPARTMENTS: readonly string[] = [
  'CSE',
  'EEE',
  'ME',
  'CE',
  'ECE',
  'IPE',
  'BME',
  'MSE',
  'LE',
  'TE',
  'CHE',
  'BECM',
  'URP',
  'ARCH',
  'ESE',
  'MME',
];

const SEMESTER_MAP: Record<string, YearTermCode> = {
  '1st': '1-1',
  '1-1': '1-1',
  '2nd': '1-2',
  '1-2': '1-2',
  '3rd': '2-1',
  '2-1': '2-1',
  '4th': '2-2',
  '2-2': '2-2',
  '5th': '3-1',
  '3-1': '3-1',
  '6th': '3-2',
  '3-2': '3-2',
  '7th': '4-1',
  '4-1': '4-1',
  '8th': '4-2',
  '4-2': '4-2',
};

export function resolveYearTerm(yearTerm?: string | null, semester?: string | null): YearTermCode | null {
  if (yearTerm) {
    const mapped = SEMESTER_MAP[yearTerm.trim().toLowerCase()];
    if (mapped) return mapped;
  }
  if (semester) {
    const mapped = SEMESTER_MAP[semester.trim().toLowerCase()];
    if (mapped) return mapped;
  }
  return null;
}

export function formatYearTermLabel(code?: string | null): string {
  if (!code) return '';
  const match = YEAR_TERM_OPTIONS.find((item) => item.code === code);
  return match ? match.label : code;
}

export const MAX_AVATAR_DATA_URI_CHARS = 32_768;

/**
 * Compresses an uploaded image file on an HTML5 <canvas> (128x128 center crop)
 * into a compact WebP/JPEG Data URI (typically 4-12 KB) suitable for $0.00 Free-Tier PostgreSQL storage.
 */
export async function compressAvatarToDataUrl(file: File): Promise<{ dataUrl: string; byteSize: number }> {
  if (!file.type.startsWith('image/') || file.type === 'image/svg+xml') {
    throw new Error('Please choose a PNG, JPEG, or WebP image file.');
  }
  if (file.size > 5 * 1024 * 1024) {
    throw new Error('Selected image must be smaller than 5 MB.');
  }

  const bitmapUrl = URL.createObjectURL(file);
  try {
    const img = await new Promise<HTMLImageElement>((resolve, reject) => {
      const element = new Image();
      element.onload = () => resolve(element);
      element.onerror = () => reject(new Error('Could not decode image file.'));
      element.src = bitmapUrl;
    });

    const size = 128;
    const canvas = document.createElement('canvas');
    canvas.width = size;
    canvas.height = size;
    const ctx = canvas.getContext('2d');
    if (!ctx) {
      throw new Error('Canvas 2D context is unavailable in this browser.');
    }

    const minSide = Math.min(img.width, img.height);
    const sx = Math.floor((img.width - minSide) / 2);
    const sy = Math.floor((img.height - minSide) / 2);
    ctx.drawImage(img, sx, sy, minSide, minSide, 0, 0, size, size);

    let dataUrl = canvas.toDataURL('image/webp', 0.8);
    if (!dataUrl.startsWith('data:image/webp')) {
      dataUrl = canvas.toDataURL('image/jpeg', 0.78);
    }
    if (dataUrl.length > MAX_AVATAR_DATA_URI_CHARS) {
      dataUrl = canvas.toDataURL('image/jpeg', 0.6);
    }
    if (dataUrl.length > MAX_AVATAR_DATA_URI_CHARS) {
      throw new Error('Compressed avatar exceeds 24 KB free-tier limit. Try a simpler image.');
    }

    const base64Part = dataUrl.split(',')[1] ?? '';
    const byteSize = Math.round((base64Part.length * 3) / 4);
    return { dataUrl, byteSize };
  } finally {
    URL.revokeObjectURL(bitmapUrl);
  }
}
