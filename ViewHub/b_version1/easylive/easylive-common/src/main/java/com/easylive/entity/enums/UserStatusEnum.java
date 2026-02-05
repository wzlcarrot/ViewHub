package com.easylive.entity.enums;
//这个类的作用是定义用户状态枚举类，包含禁用和启用两种状态。
public enum UserStatusEnum {
    DISABLED(0, "禁用"),
    ENABLED(1, "启用");

    private Integer status;
    private String desc;

    UserStatusEnum(Integer status, String desc) {
        this.status = status;
        this.desc = desc;
    }

    public static UserStatusEnum getByStatus(Integer status) {
        for (UserStatusEnum userStatusEnum : UserStatusEnum.values()) {
            if (userStatusEnum.status.equals(status)) {
                return userStatusEnum;
            }
        }
        return null;
    }

    public Integer getStatus() {
        return status;
    }
    public String getDesc() {
        return desc;
    }
    public void setDesc(String desc) {
        this.desc = desc;
    }
}
