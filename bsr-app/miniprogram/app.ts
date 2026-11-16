declare const BSR_API_ORIGIN: string | undefined;
declare const BSR_CLASSROOM_BUILD: boolean | undefined;
declare const BSR_RENTAL_STORES: Array<{ id: string; name: string }> | undefined;

App({
  globalData: {
    apiOrigin: typeof BSR_API_ORIGIN === 'undefined' ? undefined : BSR_API_ORIGIN,
    rentalStores: typeof BSR_RENTAL_STORES === 'undefined' ? [] : BSR_RENTAL_STORES,
  },
  onLaunch() {
    if (typeof BSR_CLASSROOM_BUILD === 'undefined' || !BSR_CLASSROOM_BUILD) return;
    wx.showModal({
      title: '体验前请先开启调试',
      content: '域名仍在提交审核阶段。请在右上角「…」选择「打开调试」，重新进入后再登录。如果菜单显示「关闭调试」，可直接登录。',
      showCancel: false,
      confirmText: '我知道了',
    });
  },
});
