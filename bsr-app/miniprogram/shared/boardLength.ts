// K2 Ski Sizing Guide: https://k2snow.com/en-de/support/ski-sizing-guide
// Advanced spans the published playful and charger ranges.
const skiBands = [
  [142, 125, 135, 133, 140, 138, 150],
  [152, 138, 148, 148, 155, 152, 163],
  [163, 148, 158, 158, 165, 163, 173],
  [173, 158, 168, 168, 174, 172, 183],
  [183, 168, 178, 178, 184, 182, 193],
  [193, 174, 184, 184, 190, 188, 198],
] as const;

// Jones adult length chart: https://www.jonessnowboards.com/pages/a-guide-on-how-to-find-your-perfect-snowboard-size
const snowboardBands = [
  [34, 45, 137, 143], [43, 50, 140, 145], [47, 54, 142, 147],
  [52, 58, 144, 149], [56, 63, 146, 151], [61, 68, 148, 153],
  [65, 72, 150, 155], [70, 77, 152, 157], [74, 81, 154, 159],
  [79, 86, 156, 161], [83, 91, 158, 163], [88, 95, 160, 165],
  [92, 99, 162, 167], [97, 104, 164, 169],
] as const;

export function skiLengthReference(height: string | null, skill: string): string {
  const columns = { BEGINNER: 1, INTERMEDIATE: 3, ADVANCED: 5 } as const;
  const column = Object.prototype.hasOwnProperty.call(columns, skill)
    ? columns[skill as keyof typeof columns] : undefined;
  const cm = Number(height);
  if (!column || !Number.isFinite(cm) || cm < 142 || cm > 193) return '';
  for (let index = 1; index < skiBands.length; index++) {
    const lower = skiBands[index - 1];
    const upper = skiBands[index];
    if (!lower || !upper || cm > upper[0]) continue;
    const fraction = (cm - lower[0]) / (upper[0] - lower[0]);
    const low = Math.round(lower[column] + fraction * (upper[column] - lower[column]));
    const high = Math.round(lower[column + 1]! + fraction * (upper[column + 1]! - lower[column + 1]!));
    return lengthRange(low, high);
  }
  return '';
}

export function snowboardLengthReference(weight: string | null): string {
  const kg = Number(weight);
  if (!Number.isFinite(kg) || kg < 34) return '';
  const chartWeight = Math.round(kg);
  if (chartWeight >= 105) return '165 cm 起';
  const bands = snowboardBands.filter(([min, max]) => min <= chartWeight && chartWeight <= max);
  if (!bands.length) return '';
  return lengthRange(Math.max(...bands.map(band => band[2])), Math.min(...bands.map(band => band[3])));
}

function lengthRange(lower: number, upper: number): string {
  return String(lower) + '–' + String(upper) + ' cm';
}
