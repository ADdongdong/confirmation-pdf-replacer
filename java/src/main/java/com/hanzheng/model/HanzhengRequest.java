package com.hanzheng.model;

/**
 * 函证替换请求参数（对应 Web 表单字段）
 */
public class HanzhengRequest {

    /** 致函单位名称 */
    public String companyName;

    /** 函证正文段落 */
    public String bodyText;

    /** 项目联系人 */
    public String contactPerson;

    /** 项目联系人电话 */
    public String contactPhone;

    /** 收件人 */
    public String recipient;

    /** 收件人电话 */
    public String recipientPhone;

    /** 邮箱 */
    public String email;

    /** 回函地址 */
    public String returnAddress;

    /** 页脚遮盖带高度（从页面底部向上，pt），null=默认 22 */
    public Double footerHeight;

    /** 输入 PDF 路径 */
    public String inputPath;

    /** 输出 PDF 路径 */
    public String outputPath;

    /** 字体文件路径（为 null 时自动查找） */
    public String fontFile;

    /** 用户自定义白色覆盖区域下沿 Y 坐标（PDF point，页面顶部为0向下递增），null 表示自动检测 */
    public Double whiteoutBottom;

    public HanzhengRequest() {}

    /**
     * 校验必填字段
     * @return 错误信息字符串，为 null 表示校验通过
     */
    public String validate() {
        if (companyName == null || companyName.trim().isEmpty()) {
            return "请输入致函单位";
        }
        if (bodyText == null || bodyText.trim().isEmpty()) {
            return "请输入函证正文";
        }
        if (bodyText.trim().length() > 250) {
            return "正文内容超过250字符限制（当前 " + bodyText.trim().length() + " 字符）";
        }
        if (inputPath == null || !new java.io.File(inputPath).exists()) {
            return "请上传会所函证 PDF 文件";
        }
        return null; // 通过
    }
}
