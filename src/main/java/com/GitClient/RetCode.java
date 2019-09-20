package com.GitClient;

public enum RetCode {

    MISS_ESSENTIAL_PARAM(218316,"请求参数错误，缺少必要参数"),
    REPO_CREDENTIAL_IS_NULL(218318,"代码仓库授信为空"),
    GIT_CLIENT_ERROR(218319,"GitClient获取commitId失败");

    private final int code;
    private final String msg;
    private final String detail;

    RetCode(int code, String msg) {
        this(code, msg, null);
    }

    RetCode(int code, String msg, String detail) {
        this.code = code;
        this.msg = msg;
        this.detail = detail;
    }

    public int getCode() {
        return code;
    }

    public String getMsg() {
        return msg;
    }

    public String getDetail() {
        return detail;
    }
}
