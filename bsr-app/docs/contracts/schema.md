# BSR 数据库 Schema 合同

## 状态与依据

- 当前结构为 V001—V008，共 25 张业务表、227 个字段。V001—V003 建立 24 张基线表，V004 新增补件历史表，V005—V008 扩展既有表。
- 课程设计书第 5 章数据字典和独立 ER 总图采用同一结构。
- 保留的完整业务约束依据：历史基线 SHA256 `F5E5C025D23D0F1379BB8D9DF0B1AE40F0A270B2AC07CF1813C52142F31D34D9`，以及 CR-DOC-02。论文精简未撤销状态码、事务并发、索引或业务边界。
- 直接设计落点：第 5 章 5.3—5.6，尤其表 5-1 至表 5-6、表 5-25 和表 5-26；第 6 章 6.1—6.3。
- 已关闭存储决定：B01 使用 `rental_change` 承载顾客请求；B02 使用三个独立估算布尔列；B03 在 `repair_labor` 快照实际承包商。C05 固定四类角色的服务端动作映射，不增加权限表或动作集合字段。

V001—V003 只描述原批准的持久化结构。2026-10-08 用户为 T04 明确选择保留员工补件原因与顾客每次回复的历史，V004 因此新增一张从属记录表；不增加核心分析实体、状态或权限。Flyway `flyway_schema_history` 是技术表，不计入业务表。

## 迁移编号唯一分配

| 迁移 | 固定范围 |
|---|---|
| `V001` | `customer`、`employee`、`repair_contractor`、`store`、`account`、`role_grant` 六张身份与组织表。先创建身份目标表，再创建带三种身份外键的账号表和角色授权表。 |
| `V002` | 其余 18 张业务表的基础列、主键和可在创建时安全声明的关系。 |
| `V003` | 跨表循环外键、最终 CHECK/UNIQUE 约束和业务查询索引。不得建立排班生成列唯一索引或无条件 `(employee_id, shift_date)` 唯一约束。 |
| `V004` | 用户批准的 T04 补件流程：新增 `rental_request_supplement`，每次员工原因与顾客回复各保留一条周期记录；不改 V001—V003。 |
| `V005` | 装备说明、参考零售价和档案更新版本，承包商可选邮寄地址。 |
| `V006` | `rental_change_item.effect_code` 增加 `REQUESTED_RETURN`，记录顾客拟退回意向。 |
| `V007` | 员工可选联系地址与档案备注。 |
| `V008` | 增加交易来源及模拟操作账号，按交易来源约束登记身份。 |

后续迁移从 `V009` 起由 T02 分配。禁止复制旧 11 表 V1，禁止使用 Flyway `clean` 或 `repair` 掩盖差异。

## 业务表清单（原 24 张 + V004 1 张）

| 模块 | 表 |
|---|---|
| identity | `account`、`role_grant` |
| customerrequest | `customer`、`rental_request`、`rental_request_item`、`rental_request_supplement`（V004） |
| rental | `rental_contract`、`rental_item`、`rental_change`、`rental_change_item`、`rental_charge`、`payment_transaction` |
| equipment | `equipment`、`equipment_inspection` |
| repair | `repair_contractor`、`repair_task`、`repair_rate`、`repair_labor`、`repair_service_charge` |
| storetransfer | `store`、`equipment_transfer`、`equipment_transfer_item` |
| scheduling | `employee`、`employee_shift` |
| audit | `audit_event` |

合计 25 张；V001—V003 仍严格保持原 24 张。`platform` 和 `bootstrap` 不拥有业务表。

## V001 逐列合同

类型均为 MySQL 逻辑类型。`否`表示 `NOT NULL`，`是`表示允许 `NULL`。所有内部主键均为 `BIGINT`，业务编号只作唯一业务标识，外键只引用内部主键。

### `customer`

设计来源：表 5-3；B02。

| 列 | 类型 | 可空 | 约束 |
|---|---|---:|---|
| `customer_id` | `BIGINT` | 否 | PK |
| `customer_number` | `VARCHAR(30)` | 否 | UNIQUE |
| `full_name` | `VARCHAR(100)` | 否 |  |
| `email` | `VARCHAR(255)` | 否 | UNIQUE |
| `phone` | `VARCHAR(40)` | 否 |  |
| `address` | `VARCHAR(255)` | 是 |  |
| `birth_year` | `SMALLINT` | 是 | 合理年份 CHECK；精确边界不得脱离最终设计自行扩张 |
| `height_cm` | `DECIMAL(5,2)` | 是 | `NULL` 或大于 0 |
| `weight_kg` | `DECIMAL(5,2)` | 是 | `NULL` 或大于 0 |
| `boot_size` | `DECIMAL(4,1)` | 是 | `NULL` 或大于 0 |
| `height_estimated` | `BOOLEAN` | 否 | `DEFAULT FALSE` |
| `weight_estimated` | `BOOLEAN` | 否 | `DEFAULT FALSE` |
| `boot_size_estimated` | `BOOLEAN` | 否 | `DEFAULT FALSE` |

三个估算标记必须作为独立列持久化，不得用 JSON、拼接文本或自由备注替代。

### `employee`

设计来源：表 5-4。

| 列 | 类型 | 可空 | 约束 |
|---|---|---:|---|
| `employee_id` | `BIGINT` | 否 | PK |
| `employee_number` | `VARCHAR(30)` | 否 | UNIQUE |
| `full_name` | `VARCHAR(100)` | 否 |  |
| `email` | `VARCHAR(255)` | 否 | UNIQUE |
| `phone` | `VARCHAR(40)` | 是 |  |
| `business_status` | `VARCHAR(20)` | 否 | 在职或停用状态；不得用物理删除代替 |
| `address` | `VARCHAR(255)` | 是 | V007，可选联系地址 |
| `note` | `VARCHAR(500)` | 是 | V007，可选档案备注 |

`employee` 是排班首次插入竞争的稳定父锁行，也是管理人员的业务身份；管理人员不是新增人员实体。

### `repair_contractor`

设计来源：表 5-5；B03 的目标外键表。

| 列 | 类型 | 可空 | 约束 |
|---|---|---:|---|
| `contractor_id` | `BIGINT` | 否 | PK |
| `contractor_number` | `VARCHAR(30)` | 否 | UNIQUE |
| `contractor_name` | `VARCHAR(120)` | 否 |  |
| `contact_name` | `VARCHAR(100)` | 是 |  |
| `email` | `VARCHAR(255)` | 否 | UNIQUE |
| `phone` | `VARCHAR(40)` | 是 |  |
| `business_status` | `VARCHAR(20)` | 否 | 有效或停用状态；不得用物理删除代替 |
| `postal_address` | `VARCHAR(255)` | 是 | V005，可选邮寄地址 |

`repair_contractor` 是费率区间并发检查的稳定父锁行。

### `store`

设计来源：表 5-6；OQ06 已关闭范围。

| 列 | 类型 | 可空 | 约束 |
|---|---|---:|---|
| `store_id` | `BIGINT` | 否 | PK |
| `store_number` | `VARCHAR(30)` | 否 | UNIQUE |
| `store_name` | `VARCHAR(120)` | 否 |  |
| `address` | `VARCHAR(255)` | 是 |  |
| `business_status` | `VARCHAR(20)` | 否 | 启用或停用状态；不得用物理删除代替 |
| `opening_time` | `TIME` | 否 | 小于 `closing_time`；当前业务基线 08:00 |
| `closing_time` | `TIME` | 否 | 大于 `opening_time`；当前业务基线 22:00 |

08:00—22:00 是当前排班范围，不据此增加营业日历、节假日或自动排班表。

### `account`

设计来源：表 5-1、表 5-25 的“账号只绑定一种业务身份”。

| 列 | 类型 | 可空 | 约束 |
|---|---|---:|---|
| `account_id` | `BIGINT` | 否 | PK |
| `login_name` | `VARCHAR(100)` | 否 | UNIQUE |
| `password_hash` | `VARCHAR(255)` | 否 | 只保存单向散列后的凭据 |
| `enabled` | `BOOLEAN` | 否 |  |
| `customer_id` | `BIGINT` | 是 | FK → `customer(customer_id)`，UNIQUE |
| `employee_id` | `BIGINT` | 是 | FK → `employee(employee_id)`，UNIQUE |
| `contractor_id` | `BIGINT` | 是 | FK → `repair_contractor(contractor_id)`，UNIQUE |

必须使用 CHECK 保证 `customer_id`、`employee_id`、`contractor_id` 恰有一个非空。不得增加 manager 身份列；管理人员使用 `employee_id` 加显式角色授权。

### `role_grant`

设计来源：表 5-2；第 6 章角色权限矩阵；已关闭 C05。

| 列 | 类型 | 可空 | 约束 |
|---|---|---:|---|
| `role_grant_id` | `BIGINT` | 否 | PK |
| `account_id` | `BIGINT` | 否 | FK → `account(account_id)` |
| `role_code` | `VARCHAR(30)` | 否 | 顾客、员工、管理人员或承包商四类角色 |
| `store_id` | `BIGINT` | 是 | FK → `store(store_id)`；可选显式门店范围 |
| `valid_from` | `DATETIME` | 否 |  |
| `valid_to` | `DATETIME` | 是 | `NULL` 或严格晚于 `valid_from` |

角色到动作的映射由 T03 服务端固定，不在数据库新增权限表、动作集合字段，也不得把 `store_id IS NULL` 自动解释为全局授权。MANAGER 不自动继承 EMPLOYEE。

## V001 约束与删除策略

- 所有 UNIQUE 和外键必须由数据库实际执行；外键默认限制删除，不使用级联删除清除身份或授权历史。
- `account` 三种身份外键分别唯一，并另有“三者恰一非空”CHECK；仅做三个 UNIQUE 不能替代该 CHECK。
- `role_grant.valid_to`、门店起止时间和顾客正数测量值使用数据库 CHECK 表达单行边界。
- V001 不创建第二 DataSource、权限表、管理人员表、营业日历表或演示账号。
- 行锁查询将在对应首次持久化目录中建立；本文档不把 JVM 锁或无来源锁表列为数据库保证。

## V002 逐列合同

以下 18 张表直接对应表 5-7 至表 5-24。列级约束是最终结构要求；涉及循环外键、跨表关系或集中状态校验的部分可在 V003 落地，但不得因此改变列、空值或业务含义。

### `rental_request`

设计来源：表 5-7；顾客请求不预占具体装备。

| 列 | 类型 | 可空 | 约束 |
|---|---|---:|---|
| `request_id` | `BIGINT` | 否 | PK |
| `request_number` | `VARCHAR(30)` | 否 | UNIQUE |
| `customer_id` | `BIGINT` | 否 | FK → `customer(customer_id)` |
| `service_store_id` | `BIGINT` | 否 | FK → `store(store_id)` |
| `accepted_by_employee_id` | `BIGINT` | 是 | FK → `employee(employee_id)` |
| `rental_start_date` | `DATE` | 否 |  |
| `rental_days` | `INT` | 否 | 大于 0 |
| `skill_level` | `VARCHAR(20)` | 否 |  |
| `submitted_at` | `DATETIME` | 否 |  |
| `request_status` | `VARCHAR(20)` | 否 | `PENDING`、`NEEDS_INFO`、`ACCEPTED`、`UNAVAILABLE` 或 `CONVERTED` |

### `rental_request_item`

设计来源：表 5-8。

| 列 | 类型 | 可空 | 约束 |
|---|---|---:|---|
| `request_item_id` | `BIGINT` | 否 | PK |
| `request_id` | `BIGINT` | 否 | FK → `rental_request(request_id)` |
| `equipment_category` | `VARCHAR(40)` | 否 |  |
| `quantity` | `INT` | 否 | 大于 0 |
| `fitting_note` | `VARCHAR(255)` | 是 |  |

### `rental_request_supplement`（V004 增量）

来源：用户于 2026-10-08 选择 T04 补件原因与每轮回复可追溯，而不是仅修改顾客资料后重提。该表不是 V002 原论文表 5-7/5-8 的一部分；原 Word 不修改。需求项目保持不变，不以补件覆盖原申请事实。

| 列 | 类型 | 可空 | 约束 |
|---|---|---:|---|
| `supplement_id` | `BIGINT` | 否 | PK |
| `request_id` | `BIGINT` | 否 | FK → `rental_request(request_id)`，RESTRICT |
| `cycle_number` | `INT` | 否 | 大于 0；与 `request_id` 组合 UNIQUE |
| `requested_by_employee_id` | `BIGINT` | 否 | FK → `employee(employee_id)`，RESTRICT |
| `reason` | `VARCHAR(500)` | 否 | 去空白后非空；员工说明要补充什么 |
| `requested_at` | `DATETIME` | 否 | 服务端 UTC 时间 |
| `customer_response` | `VARCHAR(500)` | 是 | 顾客的本轮补充说明；仅随 `responded_at` 一起填入 |
| `responded_at` | `DATETIME` | 是 | 服务端 UTC 时间；仅随非空回复一起填入 |

每次 `PENDING → NEEDS_INFO` 新增下一周期；顾客回复并重提时填充该周期的两个回复字段，随后回到 `PENDING`。旧周期不覆盖、不物理删除。根需求行锁保护周期编号与状态；唯一约束是并发最终防线。回复记录承载交互历史，不声称保存顾客资料的逐字段历史快照。

### `rental_contract`

设计来源：表 5-9。

| 列 | 类型 | 可空 | 约束 |
|---|---|---:|---|
| `contract_id` | `BIGINT` | 否 | PK |
| `contract_number` | `VARCHAR(30)` | 否 | UNIQUE |
| `source_request_id` | `BIGINT` | 是 | FK → `rental_request(request_id)`，UNIQUE；一份需求最多转化一次 |
| `customer_id` | `BIGINT` | 否 | FK → `customer(customer_id)` |
| `created_by_employee_id` | `BIGINT` | 否 | FK → `employee(employee_id)` |
| `service_store_id` | `BIGINT` | 否 | FK → `store(store_id)` |
| `contract_date` | `DATE` | 否 |  |
| `rental_start_date` | `DATE` | 否 |  |
| `rental_days` | `INT` | 否 | 大于 0 |
| `amount_due` | `DECIMAL(12,2)` | 否 | 大于等于 0 |
| `contract_status` | `VARCHAR(20)` | 否 | 合同八码之一 |

### `rental_item`

设计来源：表 5-10；初始费用使用日租金快照乘约定整日天数。

| 列 | 类型 | 可空 | 约束 |
|---|---|---:|---|
| `rental_item_id` | `BIGINT` | 否 | PK |
| `contract_id` | `BIGINT` | 否 | FK → `rental_contract(contract_id)` |
| `equipment_id` | `BIGINT` | 否 | FK → `equipment(equipment_id)` |
| `daily_rate_snapshot` | `DECIMAL(12,2)` | 否 | 大于等于 0 |
| `checked_out_at` | `DATETIME` | 是 |  |
| `returned_at` | `DATETIME` | 是 | `NULL` 或不早于 `checked_out_at` |
| `return_store_id` | `BIGINT` | 是 | FK → `store(store_id)` |
| `item_status` | `VARCHAR(20)` | 否 | 待发放、租出或已归还的技术码 |

### `rental_change`

设计来源：表 5-11；B01。

| 列 | 类型 | 可空 | 约束 |
|---|---|---:|---|
| `rental_change_id` | `BIGINT` | 否 | PK |
| `change_number` | `VARCHAR(30)` | 否 | UNIQUE |
| `contract_id` | `BIGINT` | 否 | FK → `rental_contract(contract_id)` |
| `change_type` | `VARCHAR(20)` | 否 | 增加、减少、更换或部分归还的技术码 |
| `change_reason` | `VARCHAR(255)` | 是 |  |
| `effective_date` | `DATE` | 是 | 正式办理确定；请求阶段为空 |
| `amount_delta` | `DECIMAL(12,2)` | 是 | 完成调整后差额；请求阶段为空，不自行增加正负限制 |
| `handled_by_employee_id` | `BIGINT` | 是 | FK → `employee(employee_id)` |
| `changed_at` | `DATETIME` | 是 | 实际办理时间；请求阶段为空 |
| `change_status` | `VARCHAR(20)` | 否 | `SUBMITTED`、`PROCESSING`、`COMPLETED` 或 `REJECTED` |
| `requested_by_customer_id` | `BIGINT` | 是 | FK → `customer(customer_id)` |
| `requested_at` | `DATETIME` | 是 |  |
| `requested_store_id` | `BIGINT` | 是 | FK → `store(store_id)` |
| `request_note` | `VARCHAR(500)` | 是 |  |

`SUBMITTED` 阶段不得预先绑定最终新增装备，也不得提前写 `rental_change_item`；正式办理后才写受影响项目。

### `rental_change_item`

设计来源：表 5-12。

| 列 | 类型 | 可空 | 约束 |
|---|---|---:|---|
| `rental_change_item_id` | `BIGINT` | 否 | PK |
| `rental_change_id` | `BIGINT` | 否 | FK → `rental_change(rental_change_id)` |
| `rental_item_id` | `BIGINT` | 否 | FK → `rental_item(rental_item_id)` |
| `effect_code` | `VARCHAR(20)` | 否 | UNIQUE (`rental_change_id`, `rental_item_id`, `effect_code`)；新增、退回、换出、换入或拟退回 |

### `rental_charge`

设计来源：表 5-13；与外部交易结果分开。

| 列 | 类型 | 可空 | 约束 |
|---|---|---:|---|
| `rental_charge_id` | `BIGINT` | 否 | PK |
| `contract_id` | `BIGINT` | 否 | FK → `rental_contract(contract_id)` |
| `rental_change_id` | `BIGINT` | 是 | FK → `rental_change(rental_change_id)` |
| `charge_type` | `VARCHAR(20)` | 否 | 初始应付、补缴或退款的技术码 |
| `amount` | `DECIMAL(12,2)` | 否 | 大于 0；方向由 `charge_type` 表达 |
| `charge_status` | `VARCHAR(20)` | 否 | 待处理、处理中、已结清或待退款的技术码 |
| `created_at` | `DATETIME` | 否 |  |

### `payment_transaction`

设计来源：表 5-14；外部结果仍由员工登记，不调用支付 API。2026-10-27 用户批准的课程虚拟付款见 specs/015，V008 增加可核验来源和真实模拟操作账号。

| 列 | 类型 | 可空 | 约束 |
|---|---|---:|---|
| `payment_transaction_id` | `BIGINT` | 否 | PK |
| `rental_charge_id` | `BIGINT` | 否 | FK → `rental_charge(rental_charge_id)` |
| `transaction_type` | `VARCHAR(20)` | 否 | 付款、补缴或退款的技术码 |
| `external_transaction_id` | `VARCHAR(100)` | 否 | UNIQUE；外部幂等标识 |
| `amount` | `DECIMAL(12,2)` | 否 | 大于 0 |
| `transaction_status` | `VARCHAR(20)` | 否 | 待确认、成功或失败的技术码 |
| `processed_at` | `DATETIME` | 是 |  |
| `recorded_by_employee_id` | `BIGINT` | 是 | FK → `employee(employee_id)`；EXTERNAL 必填，SIMULATED 必须为空 |
| `recorded_at` | `DATETIME` | 否 |  |
| `transaction_source` | `VARCHAR(20)` | 否 | EXTERNAL / SIMULATED；历史及员工登记默认 EXTERNAL |
| `simulated_by_account_id` | `BIGINT` | 是 | FK → `account(account_id)`；SIMULATED 必填，EXTERNAL 必须为空 |

V008 的 `ck_payment_origin_recorder` 保证两种来源和登记身份互斥。模拟请求采用服务端保留的 `SIM-<UUID>` 幂等命名空间，金额、操作人、时间均来自服务端；只允许顾客本人付款/补缴，不允许自助模拟退款。交易处理及登记 DATETIME 使用 UTC LocalDateTime 直写，避免 JDBC 二次时区换算。

### `equipment`

设计来源：表 5-15。

| 列 | 类型 | 可空 | 约束 |
|---|---|---:|---|
| `equipment_id` | `BIGINT` | 否 | PK |
| `inventory_number` | `VARCHAR(40)` | 否 | UNIQUE |
| `equipment_name` | `VARCHAR(120)` | 否 |  |
| `equipment_category` | `VARCHAR(40)` | 否 |  |
| `skill_level` | `VARCHAR(20)` | 否 |  |
| `home_store_id` | `BIGINT` | 否 | FK → `store(store_id)` |
| `current_store_id` | `BIGINT` | 否 | FK → `store(store_id)` |
| `equipment_status` | `VARCHAR(20)` | 否 | 装备九码之一 |
| `purchase_date` | `DATE` | 是 |  |
| `purchase_price` | `DECIMAL(12,2)` | 是 | `NULL` 或大于等于 0 |
| `current_daily_rate` | `DECIMAL(12,2)` | 否 | 大于等于 0 |
| `description` | `VARCHAR(1000)` | 是 | V005，可选装备说明 |
| `retail_price` | `DECIMAL(12,2)` | 是 | V005，非负参考零售价，独立于日租金 |
| `catalog_version` | `BIGINT` | 否 | V005，默认 0，非负档案更新版本 |

### `equipment_inspection`

设计来源：表 5-16。

| 列 | 类型 | 可空 | 约束 |
|---|---|---:|---|
| `inspection_id` | `BIGINT` | 否 | PK |
| `equipment_id` | `BIGINT` | 否 | FK → `equipment(equipment_id)` |
| `inspected_by_employee_id` | `BIGINT` | 否 | FK → `employee(employee_id)` |
| `rental_item_id` | `BIGINT` | 是 | FK → `rental_item(rental_item_id)` |
| `repair_task_id` | `BIGINT` | 是 | FK → `repair_task(repair_task_id)`；循环关系在 V003 落地 |
| `inspection_type` | `VARCHAR(20)` | 否 | 归还检查或维修验收的技术码 |
| `inspected_at` | `DATETIME` | 否 |  |
| `inspection_result` | `VARCHAR(20)` | 否 | 正常、故障或待核查的技术码 |
| `fault_description` | `VARCHAR(500)` | 是 |  |

### `repair_task`

设计来源：表 5-17。

| 列 | 类型 | 可空 | 约束 |
|---|---|---:|---|
| `repair_task_id` | `BIGINT` | 否 | PK |
| `repair_task_number` | `VARCHAR(30)` | 否 | UNIQUE |
| `equipment_id` | `BIGINT` | 否 | FK → `equipment(equipment_id)` |
| `requested_by_employee_id` | `BIGINT` | 否 | FK → `employee(employee_id)` |
| `service_store_id` | `BIGINT` | 否 | FK → `store(store_id)`；任务创建时装备实际门店快照 |
| `current_contractor_id` | `BIGINT` | 是 | FK → `repair_contractor(contractor_id)` |
| `request_date` | `DATE` | 否 |  |
| `fault_description` | `VARCHAR(500)` | 否 |  |
| `handling_requirement` | `VARCHAR(500)` | 是 |  |
| `task_status` | `VARCHAR(30)` | 否 | 维修七码之一 |
| `acceptance_result` | `VARCHAR(20)` | 是 |  |
| `accepted_by_employee_id` | `BIGINT` | 是 | FK → `employee(employee_id)` |
| `accepted_at` | `DATETIME` | 是 |  |

### `repair_rate`

设计来源：表 5-18。

| 列 | 类型 | 可空 | 约束 |
|---|---|---:|---|
| `repair_rate_id` | `BIGINT` | 否 | PK |
| `contractor_id` | `BIGINT` | 否 | FK → `repair_contractor(contractor_id)` |
| `hourly_rate` | `DECIMAL(12,2)` | 否 | 大于 0 |
| `effective_from` | `DATE` | 否 |  |
| `effective_to` | `DATE` | 是 | `NULL` 或不早于 `effective_from` |
| `created_at` | `DATETIME` | 否 |  |

同一承包商的费率区间不得重叠；并发检查锁定 `repair_contractor` 父行，不覆盖历史费率。

### `repair_labor`

设计来源：表 5-19；B03。

| 列 | 类型 | 可空 | 约束 |
|---|---|---:|---|
| `repair_labor_id` | `BIGINT` | 否 | PK |
| `repair_task_id` | `BIGINT` | 否 | FK → `repair_task(repair_task_id)` |
| `repair_date` | `DATE` | 否 |  |
| `duration_hours` | `DECIMAL(8,2)` | 否 | 大于 0 且为 0.25 的整数倍；不得自动舍入 |
| `handling_result` | `VARCHAR(500)` | 否 |  |
| `note` | `VARCHAR(500)` | 是 |  |
| `repair_rate_id` | `BIGINT` | 是 | FK → `repair_rate(repair_rate_id)` |
| `hourly_rate_snapshot` | `DECIMAL(12,2)` | 是 | `NULL` 或大于 0 |
| `labor_amount` | `DECIMAL(12,2)` | 是 | `NULL` 或大于等于 0；两位 `HALF_UP` |
| `submitted_at` | `DATETIME` | 是 |  |
| `contractor_id` | `BIGINT` | 否 | FK → `repair_contractor(contractor_id)`；实际承包商快照，后续改派不变 |

费率缺失时 `repair_rate_id`、`hourly_rate_snapshot` 和 `labor_amount` 可保持空，任务保持 `PENDING_CHARGE`；补齐时按 `contractor_id` 与 `repair_date` 匹配历史费率。

### `repair_service_charge`

设计来源：表 5-20。

| 列 | 类型 | 可空 | 约束 |
|---|---|---:|---|
| `repair_service_charge_id` | `BIGINT` | 否 | PK |
| `repair_task_id` | `BIGINT` | 否 | FK → `repair_task(repair_task_id)`，UNIQUE |
| `total_hours` | `DECIMAL(8,2)` | 否 | 大于等于 0 |
| `service_amount` | `DECIMAL(12,2)` | 否 | 大于等于 0；两位 `HALF_UP` |
| `charge_status` | `VARCHAR(20)` | 否 | 待确认或已确认的技术码 |
| `confirmed_by_employee_id` | `BIGINT` | 是 | FK → `employee(employee_id)` |
| `confirmed_at` | `DATETIME` | 是 |  |

### `equipment_transfer`

设计来源：表 5-21。

| 列 | 类型 | 可空 | 约束 |
|---|---|---:|---|
| `transfer_id` | `BIGINT` | 否 | PK |
| `transfer_number` | `VARCHAR(30)` | 否 | UNIQUE |
| `transfer_type` | `VARCHAR(20)` | 否 | 临时或永久的技术码 |
| `source_store_id` | `BIGINT` | 否 | FK → `store(store_id)` |
| `destination_store_id` | `BIGINT` | 否 | FK → `store(store_id)`；必须不同于 `source_store_id` |
| `initiated_by_employee_id` | `BIGINT` | 否 | FK → `employee(employee_id)` |
| `approved_by_employee_id` | `BIGINT` | 是 | FK → `employee(employee_id)` |
| `initiated_at` | `DATETIME` | 否 |  |
| `approved_at` | `DATETIME` | 是 |  |
| `completed_at` | `DATETIME` | 是 |  |
| `transfer_status` | `VARCHAR(20)` | 否 | 调拨状态技术码 |

发起与批准始终是两个独立动作、人员字段、时间字段和审计事件；同一有权管理人员可以分别执行两次动作。

### `equipment_transfer_item`

设计来源：表 5-22。

| 列 | 类型 | 可空 | 约束 |
|---|---|---:|---|
| `transfer_item_id` | `BIGINT` | 否 | PK |
| `transfer_id` | `BIGINT` | 否 | FK → `equipment_transfer(transfer_id)` |
| `equipment_id` | `BIGINT` | 否 | FK → `equipment(equipment_id)`；UNIQUE (`transfer_id`, `equipment_id`) |
| `dispatch_result` | `VARCHAR(20)` | 否 | 待调出、已调出或异常的技术码 |
| `dispatched_by_employee_id` | `BIGINT` | 是 | FK → `employee(employee_id)` |
| `dispatched_at` | `DATETIME` | 是 |  |
| `receipt_result` | `VARCHAR(20)` | 否 | 待接收、已接收或异常的技术码 |
| `received_by_employee_id` | `BIGINT` | 是 | FK → `employee(employee_id)` |
| `received_at` | `DATETIME` | 是 |  |
| `exception_note` | `VARCHAR(500)` | 是 |  |

临时调拨只改变装备实际门店；永久调拨同时改变通常归属门店和实际门店。

### `employee_shift`

设计来源：表 5-23；OQ06。

| 列 | 类型 | 可空 | 约束 |
|---|---|---:|---|
| `shift_id` | `BIGINT` | 否 | PK |
| `shift_number` | `VARCHAR(30)` | 否 | UNIQUE |
| `shift_date` | `DATE` | 否 |  |
| `store_id` | `BIGINT` | 否 | FK → `store(store_id)` |
| `employee_id` | `BIGINT` | 否 | FK → `employee(employee_id)` |
| `start_time` | `TIME` | 否 | 早于 `end_time`，且不得早于 08:00 |
| `end_time` | `TIME` | 否 | 不得晚于 22:00 |
| `shift_status` | `VARCHAR(20)` | 否 | 有效或已取消的技术码 |
| `changed_by_employee_id` | `BIGINT` | 否 | FK → `employee(employee_id)` |
| `changed_at` | `DATETIME` | 否 |  |
| `cancellation_reason` | `VARCHAR(255)` | 是 |  |

数据库保留普通 `(employee_id, shift_date, shift_status)` 查询索引，但不得创建生成列唯一索引或无条件 `(employee_id, shift_date)` 唯一约束。业务事务先锁 `employee` 父行，再查询有效冲突并保存，以允许取消历史共存。

### `audit_event`

设计来源：表 5-24；BR40。

| 列 | 类型 | 可空 | 约束 |
|---|---|---:|---|
| `audit_event_id` | `BIGINT` | 否 | PK |
| `actor_account_id` | `BIGINT` | 否 | FK → `account(account_id)` |
| `object_type` | `VARCHAR(40)` | 否 |  |
| `object_id` | `BIGINT` | 否 | 多业务对象内部标识，不设跨表 FK |
| `action_code` | `VARCHAR(40)` | 否 | 状态、费用、位置等关键动作技术码 |
| `occurred_at` | `DATETIME` | 否 |  |
| `result_code` | `VARCHAR(20)` | 否 | 成功、失败或冲突的技术码 |
| `detail_summary` | `VARCHAR(1000)` | 是 | 不得包含密码、会话或其他敏感凭据 |

审计历史不得由普通角色修改或删除。同一业务阶段的成功审计与业务变更在同一事务提交；失败业务不得先提交成功审计。

## V002 金额与关系核对

- 业务金额列 `amount_due`、`daily_rate_snapshot`、`amount_delta`、`rental_charge.amount`、`payment_transaction.amount`、`purchase_price`、`current_daily_rate`、`hourly_rate`、`hourly_rate_snapshot`、`labor_amount` 和 `service_amount` 均为 `DECIMAL(12,2)`。
- 工时及工时合计使用 `DECIMAL(8,2)`，不是金额列；`labor_amount` 与 `service_amount` 最终按两位 `HALF_UP` 保存。
- `rental_charge` 保存内部费用事实，`payment_transaction` 保存员工登记的外部结果和顾客虚拟付款流水，按来源约束登记身份。
- `equipment_inspection.repair_task_id` 等循环关系、完整状态 CHECK、跨字段 CHECK、最终 UNIQUE 和查询索引统一在 V003 清单核定并落地。
- 所有业务外键默认限制删除；取消、结束、失败、停用和历史快照不通过级联物理删除清除。

## V003 外键合同

所有外键使用 `fk_<表>_<列>` 命名，默认 `ON DELETE RESTRICT ON UPDATE RESTRICT`。允许空的外键在源列为 `NULL` 时不要求目标行；除此之外不得用级联删除清除历史。下表列出 55 个最终外键，未列出的 `audit_event.object_id` 等多态业务标识不得伪造跨表外键。

| 源表 | 外键列 → 目标列 |
|---|---|
| `account` | `customer_id → customer.customer_id`；`employee_id → employee.employee_id`；`contractor_id → repair_contractor.contractor_id` |
| `role_grant` | `account_id → account.account_id`；`store_id → store.store_id` |
| `rental_request` | `customer_id → customer.customer_id`；`service_store_id → store.store_id`；`accepted_by_employee_id → employee.employee_id` |
| `rental_request_item` | `request_id → rental_request.request_id` |
| `rental_contract` | `source_request_id → rental_request.request_id`；`customer_id → customer.customer_id`；`created_by_employee_id → employee.employee_id`；`service_store_id → store.store_id` |
| `rental_item` | `contract_id → rental_contract.contract_id`；`equipment_id → equipment.equipment_id`；`return_store_id → store.store_id` |
| `rental_change` | `contract_id → rental_contract.contract_id`；`handled_by_employee_id → employee.employee_id`；`requested_by_customer_id → customer.customer_id`；`requested_store_id → store.store_id` |
| `rental_change_item` | `rental_change_id → rental_change.rental_change_id`；`rental_item_id → rental_item.rental_item_id` |
| `rental_charge` | `contract_id → rental_contract.contract_id`；`rental_change_id → rental_change.rental_change_id` |
| `payment_transaction` | `rental_charge_id → rental_charge.rental_charge_id`；`recorded_by_employee_id → employee.employee_id`；`simulated_by_account_id → account.account_id` |
| `equipment` | `home_store_id → store.store_id`；`current_store_id → store.store_id` |
| `equipment_inspection` | `equipment_id → equipment.equipment_id`；`inspected_by_employee_id → employee.employee_id`；`rental_item_id → rental_item.rental_item_id`；`repair_task_id → repair_task.repair_task_id` |
| `repair_task` | `equipment_id → equipment.equipment_id`；`requested_by_employee_id → employee.employee_id`；`service_store_id → store.store_id`；`current_contractor_id → repair_contractor.contractor_id`；`accepted_by_employee_id → employee.employee_id` |
| `repair_rate` | `contractor_id → repair_contractor.contractor_id` |
| `repair_labor` | `repair_task_id → repair_task.repair_task_id`；`repair_rate_id → repair_rate.repair_rate_id`；`contractor_id → repair_contractor.contractor_id` |
| `repair_service_charge` | `repair_task_id → repair_task.repair_task_id`；`confirmed_by_employee_id → employee.employee_id` |
| `equipment_transfer` | `source_store_id → store.store_id`；`destination_store_id → store.store_id`；`initiated_by_employee_id → employee.employee_id`；`approved_by_employee_id → employee.employee_id` |
| `equipment_transfer_item` | `transfer_id → equipment_transfer.transfer_id`；`equipment_id → equipment.equipment_id`；`dispatched_by_employee_id → employee.employee_id`；`received_by_employee_id → employee.employee_id` |
| `employee_shift` | `store_id → store.store_id`；`employee_id → employee.employee_id`；`changed_by_employee_id → employee.employee_id` |
| `audit_event` | `actor_account_id → account.account_id` |

`equipment_inspection.repair_task_id` 在 `equipment_inspection` 与 `repair_task` 均已创建后由 V003 增加，以明确处理循环依赖。其他 V002 可安全声明的外键即使在建表时创建，也必须与本清单同名同义。

## V003 UNIQUE 合同

除主键外，最终结构包含以下 23 个 UNIQUE 约束。命名使用 `uk_<表>_<列组合>`；不得额外把历史关系变成唯一关系。

| 表 | UNIQUE 列 |
|---|---|
| `customer` | `customer_number`；`email` |
| `employee` | `employee_number`；`email` |
| `repair_contractor` | `contractor_number`；`email` |
| `store` | `store_number` |
| `account` | `login_name`；`customer_id`；`employee_id`；`contractor_id` |
| `rental_request` | `request_number` |
| `rental_contract` | `contract_number`；`source_request_id` |
| `rental_change` | `change_number` |
| `rental_change_item` | (`rental_change_id`, `rental_item_id`, `effect_code`) |
| `payment_transaction` | `external_transaction_id` |
| `equipment` | `inventory_number` |
| `repair_task` | `repair_task_number` |
| `repair_service_charge` | `repair_task_id` |
| `equipment_transfer` | `transfer_number` |
| `equipment_transfer_item` | (`transfer_id`, `equipment_id`) |
| `employee_shift` | `shift_number` |

`employee_shift` 不得增加 (`employee_id`, `shift_date`) 唯一约束或生成列唯一索引；取消历史必须能够与同日有效班次共存。`rental_item.equipment_id`、`repair_task.equipment_id` 和历史费率列也不得设为全历史唯一。

## V003 技术码 CHECK 字典

以下英文值只冻结设计中已有中文状态或类别的技术拼写，不增加业务状态。所有状态迁移仍由领域动作校验，数据库 CHECK 只阻止未知值。

| 列 | 允许值 |
|---|---|
| `employee.business_status`、`repair_contractor.business_status`、`store.business_status` | `ACTIVE`、`INACTIVE` |
| `role_grant.role_code` | `CUSTOMER`、`EMPLOYEE`、`MANAGER`、`CONTRACTOR` |
| `rental_request.request_status` | `PENDING`、`NEEDS_INFO`、`ACCEPTED`、`UNAVAILABLE`、`CONVERTED` |
| `rental_contract.contract_status` | `DRAFT`、`PENDING_CONFIRMATION`、`PENDING_PAYMENT`、`PENDING_ISSUE`、`ACTIVE`、`PENDING_SETTLEMENT`、`ENDED`、`CANCELLED` |
| `rental_item.item_status` | `PENDING_ISSUE`、`RENTED`、`RETURNED` |
| `rental_change.change_type` | `ADD`、`REMOVE`、`REPLACE`、`PARTIAL_RETURN` |
| `rental_change.change_status` | `SUBMITTED`、`PROCESSING`、`COMPLETED`、`REJECTED` |
| `rental_change_item.effect_code` | `ADD`、`RETURN`、`REPLACE_OUT`、`REPLACE_IN`、`REQUESTED_RETURN`（V006） |
| `rental_charge.charge_type` | `INITIAL`、`SUPPLEMENT`、`REFUND` |
| `rental_charge.charge_status` | `PENDING`、`PROCESSING`、`SETTLED`、`PENDING_REFUND` |
| `payment_transaction.transaction_type` | `PAYMENT`、`SUPPLEMENT`、`REFUND` |
| `payment_transaction.transaction_status` | `PENDING_CONFIRMATION`、`SUCCEEDED`、`FAILED` |
| `equipment.equipment_status` | `AVAILABLE`、`PENDING_ISSUE`、`RENTED`、`RETURN_INSPECTION`、`WAITING_REPAIR`、`IN_REPAIR`、`PENDING_ACCEPTANCE`、`IN_TRANSFER`、`PENDING_REVIEW` |
| `equipment_inspection.inspection_type` | `RETURN`、`REPAIR_ACCEPTANCE` |
| `equipment_inspection.inspection_result` | `NORMAL`、`FAULT`、`PENDING_REVIEW` |
| `repair_task.task_status` | `UNASSIGNED`、`PENDING`、`IN_PROGRESS`、`PENDING_ACCEPTANCE`、`RETURNED`、`PENDING_CHARGE`、`COMPLETED` |
| `repair_task.acceptance_result` | `PASSED`、`FAILED`、`NEEDS_INFO`；或 `NULL` |
| `repair_service_charge.charge_status` | `PENDING_CONFIRMATION`、`CONFIRMED` |
| `equipment_transfer.transfer_type` | `TEMPORARY`、`PERMANENT` |
| `equipment_transfer.transfer_status` | `INITIATED`、`APPROVED`、`IN_TRANSIT`、`PARTIALLY_RECEIVED`、`COMPLETED` |
| `equipment_transfer_item.dispatch_result` | `PENDING`、`DISPATCHED`、`EXCEPTION` |
| `equipment_transfer_item.receipt_result` | `PENDING`、`RECEIVED`、`EXCEPTION` |
| `employee_shift.shift_status` | `ACTIVE`、`CANCELLED` |
| `audit_event.result_code` | `SUCCESS`、`FAILURE`、`CONFLICT` |

技能等级、装备类别、审计对象/动作等开放业务词汇不在 T02 擅自枚举。后续 owner 如需扩展上表中的封闭码集，必须先更新对应业务合同和迁移，不能只绕过 CHECK。

## V003 数值与单行 CHECK 合同

约束名使用 `ck_<表>_<含义>`。SQL 实现可作等价括号化，但不得弱化下列语义。

| 约束 | 必须表达的条件 |
|---|---|
| `ck_customer_birth_year` | `birth_year IS NULL OR birth_year > 0`；仅作无来源年龄阈值之外的最小存储校验，当前年份校验由业务层完成 |
| `ck_customer_height_positive` | `height_cm IS NULL OR height_cm > 0` |
| `ck_customer_weight_positive` | `weight_kg IS NULL OR weight_kg > 0` |
| `ck_customer_boot_size_positive` | `boot_size IS NULL OR boot_size > 0` |
| `ck_store_opening_before_closing` | `opening_time < closing_time` |
| `ck_account_exactly_one_identity` | 三个身份外键恰有一个非空 |
| `ck_role_grant_valid_range` | `valid_to IS NULL OR valid_to > valid_from` |
| `ck_rental_request_days_positive` | `rental_days > 0` |
| `ck_rental_request_item_quantity_positive` | `quantity > 0` |
| `ck_rental_contract_days_positive` | `rental_days > 0` |
| `ck_rental_contract_amount_due_nonnegative` | `amount_due >= 0` |
| `ck_rental_item_daily_rate_nonnegative` | `daily_rate_snapshot >= 0` |
| `ck_rental_item_return_order` | `returned_at IS NULL OR checked_out_at IS NULL OR returned_at >= checked_out_at` |
| `ck_rental_charge_amount_positive` | `amount > 0`；退款方向由类型表达，不保存负金额 |
| `ck_payment_transaction_amount_positive` | `amount > 0` |
| `ck_equipment_purchase_price_nonnegative` | `purchase_price IS NULL OR purchase_price >= 0` |
| `ck_equipment_daily_rate_nonnegative` | `current_daily_rate >= 0` |
| `ck_repair_rate_positive` | `hourly_rate > 0` |
| `ck_repair_rate_date_range` | `effective_to IS NULL OR effective_to >= effective_from` |
| `ck_repair_labor_quarter_hour` | `duration_hours > 0` 且为 `0.25` 的整数倍 |
| `ck_repair_labor_rate_positive` | `hourly_rate_snapshot IS NULL OR hourly_rate_snapshot > 0` |
| `ck_repair_labor_amount_nonnegative` | `labor_amount IS NULL OR labor_amount >= 0` |
| `ck_repair_service_hours_nonnegative` | `total_hours >= 0` |
| `ck_repair_service_amount_nonnegative` | `service_amount >= 0` |
| `ck_transfer_distinct_stores` | `source_store_id <> destination_store_id` |
| `ck_shift_time_order` | `start_time < end_time` |
| `ck_shift_business_hours` | `start_time >= '08:00:00' AND end_time <= '22:00:00'` |

技术码字典中的每一列另有同名语义的 `IN (...)` CHECK。字符串业务编号、名称、联系方式和说明的去空白/非空内容由应用层输入校验，不在数据库中引入无来源长度以外规则。

## V003 跨字段阶段 CHECK 合同

这些约束只表达设计已明确的同一行完整性，跨行、跨表和状态迁移合法性仍由业务事务负责。

| 约束 | 必须表达的条件 |
|---|---|
| `ck_rental_change_submitted_fields` | `SUBMITTED` 时请求顾客、请求时间、请求门店非空，且经办员工、生效日、差额、办理时间为空 |
| `ck_rental_change_completed_fields` | `COMPLETED` 时经办员工、生效日、差额、办理时间全部非空 |
| `ck_repair_task_acceptance_fields` | `acceptance_result`、`accepted_by_employee_id`、`accepted_at` 三者同时为空或同时非空 |
| `ck_repair_service_confirmation_fields` | `PENDING_CONFIRMATION` 时确认员工/时间为空；`CONFIRMED` 时二者非空 |
| `ck_transfer_approval_fields` | `approved_by_employee_id` 与 `approved_at` 同时为空或同时非空；不要求批准人与发起人不同 |
| `ck_transfer_completion_time` | `transfer_status = 'COMPLETED'` 时 `completed_at` 非空；其他状态不强制清空历史时间 |
| `ck_transfer_item_dispatch_fields` | `dispatch_result = 'PENDING'` 时调出员工/时间为空；其他结果时二者非空 |
| `ck_transfer_item_receipt_fields` | `receipt_result = 'PENDING'` 时接收员工/时间为空；其他结果时二者非空 |

数据库 CHECK 不尝试表达下列规则：同一装备同时只能处于一个有效租赁/维修/调拨流程、同承包商费率区间不重叠、员工同日最多一个有效班次、合同结束聚合条件、维修费率与维修日期匹配。这些规则必须按既定锁顺序在 `READ_COMMITTED` 业务事务中查询、复核并保存。

## V003 业务查询索引合同

以下 16 个命名索引来自表 5-6；UNIQUE 同时承担唯一检查与相应定位查询。

| 索引 | 列 | 支持查询 |
|---|---|---|
| `uk_customer_email` | `customer(email)` | 按电子邮箱定位顾客 |
| `uk_customer_number` | `customer(customer_number)` | 按客户编号核验顾客 |
| `uk_contract_number` | `rental_contract(contract_number)` | 打开合同和办理归还 |
| `idx_contract_customer_status` | `rental_contract(customer_id, contract_status)` | 顾客当前及历史租赁 |
| `uk_equipment_inventory_number` | `equipment(inventory_number)` | 逐件扫描和核对 |
| `idx_equipment_location_status` | `equipment(current_store_id, equipment_status, equipment_category)` | 当前门店可租装备与跨店查询 |
| `idx_rental_item_equipment_time` | `rental_item(equipment_id, checked_out_at, returned_at)` | 装备租赁历史 |
| `idx_repair_task_contractor_status` | `repair_task(current_contractor_id, task_status)` | 承包商本人待办任务 |
| `idx_repair_task_equipment_status` | `repair_task(equipment_id, task_status)` | 检查装备未结束维修任务 |
| `idx_repair_rate_contractor_dates` | `repair_rate(contractor_id, effective_from, effective_to)` | 按维修日期匹配历史费率 |
| `idx_shift_employee_date_status` | `employee_shift(employee_id, shift_date, shift_status)` | 员工同日冲突与本人排班 |
| `idx_shift_store_date` | `employee_shift(store_id, shift_date, shift_status)` | 门店排班查询 |
| `idx_transfer_status_time` | `equipment_transfer(transfer_status, initiated_at)` | 跟踪未完成调拨 |
| `idx_transfer_item_equipment` | `equipment_transfer_item(equipment_id, transfer_id)` | 装备调拨历史 |
| `uk_payment_external_id` | `payment_transaction(external_transaction_id)` | 外部交易幂等核对 |
| `idx_audit_object_time` | `audit_event(object_type, object_id, occurred_at)` | 按业务对象追踪关键变更 |

每个外键还必须由以该外键列开头的 UNIQUE、上述组合索引或显式 `idx_fk_<表>_<列>` 支持；已有前导索引时不得再建完全冗余索引。V003 校验脚本必须检查所有 55 个外键均满足此前导规则。低选择性状态列不得单独建索引，覆盖索引只在真实执行计划证明需要时另行迁移。

## V003 并发与删除边界

- 排班：锁 `employee` 父行后按 (`employee_id`, `shift_date`, `shift_status`) 查询有效冲突；不得用无条件唯一或生成列唯一替代。
- 维修任务首次插入：先锁 `equipment`，再以 READ_COMMITTED 普通查询检查未结束任务，不反向锁任务行形成环。
- 历史费率：锁 `repair_contractor`，检查同承包商日期区间重叠；新增区间，不覆盖历史。
- 租赁与调拨：按门店、主体、根单据、`equipment_id` 升序和明细的既定顺序锁定并复核状态。
- 历史合同、项目、费用、交易、工时、费率、调拨、班次和审计不提供常规物理删除或级联删除路径。

## 迁移验证入口

原 24 表逐列合同及 V003 FK/CHECK/UNIQUE/索引合同由 V001—V003、24 个 JPA Row/Repository 和迁移布局门禁实现；V004 在此基础上增加一表、一 Row/Repository。集成测试只接受显式的 `TEST_DB_HOST`、`TEST_DB_PORT`、`TEST_DB_NAME`、`TEST_DB_USERNAME`、`TEST_DB_PASSWORD`；库名必须匹配 `bsr_test_*` 或 `bsr_it_*` 的小写安全前缀，缺少任一变量时在连接前失败。

首次对空测试库执行 `-Pit verify` 时，初始化器先迁移到 V002，加载覆盖原 24 张表的虚构历史数据，再执行 V003—V008。测试检查历史记录、JPA 校验及 58 个外键、24 个唯一约束和 67 个 CHECK 约束。已有 V003—V007 的隔离库补齐剩余迁移，V008 再运行时应执行零次迁移。测试只使用符合命名前缀要求的隔离库。

V004 由 2026-10-08 用户批准的 T04 补件历史需求触发，实际新增 `rental_request_supplement` 并保留 V001—V003 原始校验结果。后续 V005+ 仍需真实可追溯的结构变化；不得以空迁移、重复迁移或演示数据迁移凑编号。
