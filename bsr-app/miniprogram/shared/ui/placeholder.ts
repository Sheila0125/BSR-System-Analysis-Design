export interface PlaceholderCopy {
  readonly title: string;
  readonly message: string;
}

export function foundationPlaceholder(title: string): PlaceholderCopy {
  return {
    title,
    message: 'Engineering placeholder only. Business behavior is implemented by its owning task.',
  };
}
