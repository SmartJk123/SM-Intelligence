// Small chart helper used to create smooth SVG paths from numeric data points.
/** Cubic segments stay within each pair of values; no fabricated extrema. */
export function smoothChartPath(values: number[], maximum: number): string {
  if (!values.length) return '';
  const coords = values.map((value, i) => ({
    x: 10 + i * 580 / Math.max(1, values.length - 1),
    y: 180 - value / Math.max(1, maximum) * 170,
  }));
  let path = 'M' + coords[0].x + ',' + coords[0].y;
  for (let i = 1; i < coords.length; i++) {
    const a = coords[i-1], b = coords[i], middle = (a.x+b.x)/2;
    path += ' C' + middle + ',' + a.y + ' ' + middle + ',' + b.y + ' ' + b.x + ',' + b.y;
  }
  return path;
}
