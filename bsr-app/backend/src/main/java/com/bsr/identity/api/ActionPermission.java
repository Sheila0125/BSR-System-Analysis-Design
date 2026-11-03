package com.bsr.identity.api;

public enum ActionPermission {
    CUSTOMER_PROFILE_SELF(RoleCode.CUSTOMER, false),
    CUSTOMER_RENTAL_REQUEST(RoleCode.CUSTOMER, false),
    CUSTOMER_RENTAL_READ(RoleCode.CUSTOMER, false),
    CUSTOMER_PAYMENT_SIMULATE(RoleCode.CUSTOMER, false),
    CUSTOMER_CHANGE_REQUEST(RoleCode.CUSTOMER, false),

    EMPLOYEE_RENTAL_PROCESS(RoleCode.EMPLOYEE, true),
    EMPLOYEE_PAYMENT_RECORD(RoleCode.EMPLOYEE, true),
    EMPLOYEE_RETURN_AND_INSPECT(RoleCode.EMPLOYEE, true),
    EMPLOYEE_REPAIR_PROCESS(RoleCode.EMPLOYEE, true),
    EMPLOYEE_EQUIPMENT_QUERY(RoleCode.EMPLOYEE, true),
    EMPLOYEE_TRANSFER_HANDOFF(RoleCode.EMPLOYEE, true),
    EMPLOYEE_SHIFT_SELF_READ(RoleCode.EMPLOYEE, false),

    MANAGER_EQUIPMENT_OVERVIEW(RoleCode.MANAGER, true),
    MANAGER_EQUIPMENT_MAINTAIN(RoleCode.MANAGER, true),
    MANAGER_TRANSFER(RoleCode.MANAGER, true),
    MANAGER_STORE_AND_SCHEDULING(RoleCode.MANAGER, true),
    MANAGER_CONTRACTOR_AND_RATE(RoleCode.MANAGER, true),
    MANAGER_OPERATIONS_READ(RoleCode.MANAGER, true),
    MANAGER_ROLE_GRANT(RoleCode.MANAGER, false),
    MANAGER_AUDIT_READ(RoleCode.MANAGER, false),

    CONTRACTOR_REPAIR_TASK(RoleCode.CONTRACTOR, false),
    CONTRACTOR_SERVICE_CHARGE_READ(RoleCode.CONTRACTOR, false);

    private final RoleCode requiredRole;
    private final boolean storeScoped;

    ActionPermission(RoleCode requiredRole, boolean storeScoped) {
        this.requiredRole = requiredRole;
        this.storeScoped = storeScoped;
    }

    public RoleCode requiredRole() {
        return requiredRole;
    }

    public boolean storeScoped() {
        return storeScoped;
    }
}
