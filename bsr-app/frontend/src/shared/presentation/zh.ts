import { ApiError } from '../http/apiClient'

const labels: Record<string, string> = {
  CUSTOMER: '顾客', EMPLOYEE: '员工', MANAGER: '门店经理', CONTRACTOR: '维修承包商',
  AVAILABLE: '可租', WAITING_REPAIR: '待维修', IN_REPAIR: '维修中', RENTED: '已租出',
  RESERVED: '已预留', PENDING_ISSUE: '待发放', TRANSFERRING: '调拨中', IN_TRANSFER: '调拨中',
  PENDING_REVIEW: '待复核', LOST: '遗失', SCRAPPED: '报废', DISABLED: '已停用',
  DRAFT: '草稿', PENDING_CONFIRMATION: '待确认', PENDING_PAYMENT: '待付款',
  ACTIVE: '有效', INACTIVE: '已停用', CANCELLED: '已取消', COMPLETED: '已完成',
  RETURNED: '已归还', UNASSIGNED: '未分派', PENDING: '待处理', IN_PROGRESS: '进行中',
  PENDING_ACCEPTANCE: '待验收', PENDING_CHARGE: '待确认费用', SUBMITTED: '已提交',
  PROCESSING: '办理中', ACCEPTED: '已受理', UNAVAILABLE: '无法满足', CONVERTED: '已转合同',
  NEEDS_SUPPLEMENT: '待补充资料', SUPPLEMENT_REQUIRED: '待补充资料', SETTLED: '已结清',
  APPROVED: '已批准', DISPATCHED: '已发出', RECEIVED: '已接收', EXCEPTION: '交接异常',
  TEMPORARY: '临时调拨', PERMANENT: '永久调拨', PAYMENT: '付款', SUPPLEMENT: '补缴',
  REFUND: '退款', SUCCEEDED: '成功', SUCCESS: '成功', FAILED: '失败', FAILURE: '失败',
  NORMAL: '正常', FAULT: '故障', ADD: '新增', REMOVE: '减少', REPLACE: '更换',
  PARTIAL_RETURN: '部分归还', REPLACE_IN: '更换新增', REPLACE_OUT: '更换退回',
  INITIAL: '初始租赁费', UNSETTLED: '待结清', PASSED: '通过', DISPATCH: '发出', RECEIPT: '接收',
  SKI: '双板', SNOWBOARD: '单板', BOOTS: '雪鞋', SKI_BOOTS: '双板雪鞋',
  NEEDS_INFO: '待补充资料', AWAITING_CUSTOMER: '待顾客确认', AWAITING_PAYMENT: '待付款',
  PENDING_SETTLEMENT: '待结算', ENDED: '已结束', RETURN_INSPECTION: '归还检查中',
  INITIATED: '已发起', IN_TRANSIT: '运输中', PARTIALLY_RECEIVED: '部分接收',
  CONFIRMED: '已确认', DECLINED: '已拒绝', REJECTED: '已拒绝', READY: '待发放',
  POLES: '雪杖', HELMET: '头盔', GOGGLES: '雪镜', BEGINNER: '初级', INTERMEDIATE: '中级', ADVANCED: '高级',
}

/** Display only: never translate values used in requests, permissions or transitions. */
export function uiText(value: unknown, context?: 'repair'): string {
  if (value === null || value === undefined || value === '') return '—'
  const code = String(value)
  if (context === 'repair' && code === 'RETURNED') return '退回返工'
  return labels[code] ?? code
}

const errors: Record<string, string> = {
  BAD_CREDENTIALS: '账号或密码不正确，请重新输入。',
  SESSION_INVALID: '登录已失效，请重新登录。', SESSION_EXPIRED: '登录已过期，请重新登录。',
  UNAUTHENTICATED: '请先登录后再操作。', UNAUTHORIZED: '请先登录后再操作。',
  ACCOUNT_DISABLED: '账号已停用，请联系门店管理员。', IDENTITY_INACTIVE: '业务身份已停用，请联系门店管理员。',
  SESSION_REVOKED: '账号授权已失效，请联系门店管理员。',
  ACCOUNT_IDENTITY_INVALID: '账号身份配置异常，请联系门店管理员。',
  ROLE_IDENTITY_MISMATCH: '账号身份与角色不匹配，请联系门店管理员。',
  CSRF_INVALID: '页面安全验证已失效，请刷新后重试。',
  CSRF_REJECTED: '页面安全验证已失效，请刷新后重试。',
  VALIDATION_FAILED: '输入内容不符合要求，请检查必填项、编号、日期及金额格式。',
  AUTHENTICATION_REQUIRED: '请先登录后再操作。', ACCESS_DENIED: '您没有执行此操作的权限。',
  ACTION_NOT_AUTHORIZED: '当前账号未获得此操作的授权。',
  STORE_SCOPE_DENIED: '该门店不在您的授权范围内。', CUSTOMER_SCOPE_DENIED: '该顾客不在您的授权范围内。',
  CONTRACTOR_SCOPE_DENIED: '该承包商不在您的授权范围内。', TRANSFER_SCOPE_DENIED: '调拨门店不在您的授权范围内。',
  ACCOUNT_NOT_FOUND: '未找到该账号，请核对账号编号。', CUSTOMER_NOT_FOUND: '未找到该顾客，请核对顾客编号或邮箱。',
  STORE_NOT_FOUND: '未找到该门店，请核对门店编号。', STORE_INACTIVE: '该门店已停用。',
  STORE_NUMBER_EXISTS: '该门店编号已被使用，请更换编号。', INVALID_STORE_DETAILS: '门店资料或营业时间不符合要求。',
  INVALID_STORE_ID: '请输入有效的正整数门店编号。', INVALID_STORE_NUMBER: '门店编号须为 1—30 个字符。',
  CUSTOMER_PROFILE_CHANGED: '顾客尺寸资料已发生变化，请重新查询后核对。',
  EMPLOYEE_IDENTITY_REQUIRED: '此操作需要员工身份。', MANAGER_IDENTITY_REQUIRED: '此操作需要门店经理授权。',
  ROLE_GRANT_NOT_FOUND: '未找到该授权记录。', ROLE_GRANT_NOT_ACTIVE: '只能撤销当前有效的授权。',
  ROLE_GRANT_OVERLAP: '授权时间与既有授权重叠，请调整有效期。', ROLE_GRANT_RANGE_INVALID: '失效时间必须晚于生效时间。',
  RENTAL_CONTRACT_NOT_FOUND: '未找到该租赁合同。', RENTAL_CHANGE_NOT_FOUND: '未找到该租赁调整。',
  RENTAL_CHARGE_NOT_FOUND: '未找到该费用记录。', RENTAL_REQUEST_NOT_FOUND: '未找到该租赁需求。',
  RENTAL_REQUEST_ALREADY_CONVERTED: '该需求已生成合同，请查询已有合同。',
  RENTAL_REQUEST_CONTRACT_MISMATCH: '合同与来源需求、顾客或门店不一致。',
  RENTAL_REQUEST_STATE_CONFLICT: '需求状态不支持当前操作，请刷新后检查是否已受理及是否有待回复资料。',
  RENTAL_LOCK_CONFLICT: '其他人正在处理该记录，请刷新后重试。',
  REPAIR_CONCURRENT_CONFLICT: '维修记录已被其他操作修改，请刷新后重试。',
  SHIFT_CONCURRENT_CONFLICT: '班次已被其他操作修改，请刷新后重试。', SHIFT_CHANGED: '班次已发生变化，请刷新后重试。',
  SHIFT_NOT_FOUND: '未找到该班次。', SHIFT_NOT_ACTIVE: '只能调整或取消有效班次。', SHIFT_NOT_FUTURE: '只能取消未来的班次。',
  STORE_HAS_ACTIVE_TRANSFER: '门店仍有未结束的调拨，暂不能停用。',
  STORE_HAS_FUTURE_SHIFT: '门店仍有未来有效班次，暂不能停用。',
  STORE_HAS_UNFINISHED_RENTAL: '门店仍有未结束的租赁合同，暂不能停用。',
  STORE_CONCURRENT_CONFLICT: '门店资料已发生变化，请刷新后重试。',
  STORE_TRANSFER_CONCURRENT_CONFLICT: '门店或调拨记录已发生变化，请刷新后重试。',
  TRANSFER_NOT_FOUND: '未找到该调拨记录。', TRANSFER_CHANGED: '调拨门店已发生变化，请刷新后重试。',
  TRANSFER_ITEM_NOT_FOUND: '所选雪具不属于该调拨。',
  TRANSFER_EQUIPMENT_UNAVAILABLE: '部分雪具已无法从调出门店调拨，请刷新后重新选择。',
  INVALID_TRANSFER_STORES: '调出与调入门店必须不同，请核对门店编号。',
}

export function problemText(problem: { code: string; status: number; detail: string }): string {
  if (errors[problem.code]) return errors[problem.code]
  if (/[\u3400-\u9fff]/.test(problem.detail)) return problem.detail
  const fallback: Record<number, string> = {
    400: '输入内容或操作不符合要求，请检查后重试。',
    401: '登录已失效，请重新登录。', 403: '您没有执行此操作的权限，请核对角色及门店授权。',
    404: '未找到记录，请核对编号及当前门店。',
    409: '当前状态存在冲突，请刷新记录后核对库存、日期、费用及业务状态。',
    422: '当前操作不符合业务规则，请核对输入及记录状态。',
  }
  return `${fallback[problem.status] ?? '服务暂时不可用，请稍后重试。'}（${problem.code}）`
}

export function errorText(error: unknown): string {
  if (error instanceof ApiError) return problemText(error.problem)
  if (error instanceof Error && /[\u3400-\u9fff]/.test(error.message)) return error.message
  if (error instanceof Error && /^A positive (decimal identifier|identifier string) is required\.$/.test(error.message)) return '请输入有效的正整数编号。'
  if (error instanceof TypeError && /fetch|network|connection/i.test(error.message)) return '连接失败，请检查网络后重试。'
  // Preserve unexpected diagnostic detail rather than hiding inventory IDs or conflict evidence.
  return error instanceof Error ? `操作失败：${error.message}` : '操作失败，请检查连接后重试。'
}
