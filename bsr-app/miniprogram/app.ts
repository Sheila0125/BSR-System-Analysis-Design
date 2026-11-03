declare const BSR_API_ORIGIN: string | undefined;

App({
  globalData: {
    apiOrigin: typeof BSR_API_ORIGIN === 'undefined' ? undefined : BSR_API_ORIGIN,
  },
});
