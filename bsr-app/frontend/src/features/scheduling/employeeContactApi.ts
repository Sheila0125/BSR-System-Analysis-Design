import { ApiError, type ApiProblem } from '../../shared/http/apiClient'
import { fetchCsrf } from '../identity/authApi'

export interface EmployeeContact {
  employeeId: string; employeeNumber: string; fullName: string; email: string
  phone: string | null; address: string | null; note: string | null
}
function id(value: string) {
  if (!/^[1-9][0-9]{0,18}$/.test(value)) throw new Error('请填写有效的门店和员工编号。')
  return value
}
async function decode(response: Response): Promise<EmployeeContact> {
  if (!response.ok) throw new ApiError(await response.json() as ApiProblem)
  return response.json() as Promise<EmployeeContact>
}
export const employeeContactApi = {
  async read(storeId: string, employeeId: string) {
    return decode(await fetch(`/api/employees/${id(employeeId)}/contact?storeId=${id(storeId)}`, { credentials: 'same-origin' }))
  },
  async update(storeId: string, employeeId: string, address: string, note: string) {
    const path = `/api/employees/${id(employeeId)}/contact`
    id(storeId)
    const csrf = await fetchCsrf()
    return decode(await fetch(path, { method: 'POST', credentials: 'same-origin',
      headers: { 'Content-Type': 'application/json', [csrf.headerName]: csrf.token },
      body: JSON.stringify({ storeId, address, note }) }))
  },
}

export interface EmployeeChoice { employeeId: string; employeeNumber: string; fullName: string }
export async function employeeChoices(storeId: string): Promise<EmployeeChoice[]> {
  const response = await fetch(`/api/employees?storeId=${id(storeId)}`, { credentials: 'same-origin' })
  if (!response.ok) throw new ApiError(await response.json() as ApiProblem)
  return response.json() as Promise<EmployeeChoice[]>
}
