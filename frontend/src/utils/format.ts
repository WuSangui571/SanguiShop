export function formatMoney(cents: number | null | undefined): string {
  if (typeof cents !== 'number' || Number.isNaN(cents)) {
    return '--'
  }

  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: 'CNY',
    minimumFractionDigits: 2,
  }).format(cents / 100)
}

export function formatDateTime(value: string | null | undefined): string {
  if (!value) {
    return '--'
  }

  const parsed = new Date(value)
  if (Number.isNaN(parsed.getTime())) {
    return value
  }

  return new Intl.DateTimeFormat('en-GB', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
  }).format(parsed)
}

export function formatCount(value: number | null | undefined): string {
  if (typeof value !== 'number' || Number.isNaN(value)) {
    return '0'
  }

  return new Intl.NumberFormat('en-US').format(value)
}
