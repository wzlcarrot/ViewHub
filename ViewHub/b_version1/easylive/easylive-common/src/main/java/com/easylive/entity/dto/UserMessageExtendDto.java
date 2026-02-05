package com.easylive.entity.dto;

//这个类主要用在用户消息系统中，根据不同类型的消息存储相应的扩展信息.这个对象最终会被转换成JSON格式存储到数据库的消息表中，作为消息的扩展信息字段。
//其实这儿字段你也可以理解成预留字段。。因为在以后，，你也不知道需要加上什么字段，，所以留一个字段来为了以后
public class UserMessageExtendDto {
    private String messageContent;

    private String messageContentReply;

    //审核状态
    private Integer auditStatus;

    public String getMessageContent() {
        return messageContent;
    }

    public void setMessageContent(String messageContent) {
        this.messageContent = messageContent;
    }

    public String getMessageContentReply() {
        return messageContentReply;
    }

    public void setMessageContentReply(String messageContentReply) {
        this.messageContentReply = messageContentReply;
    }

    public Integer getAuditStatus() {
        return auditStatus;
    }

    public void setAuditStatus(Integer auditStatus) {
        this.auditStatus = auditStatus;
    }
}
