package com.yoyo.jingxi.ui.dialog;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.yoyo.jingxi.R;

/**
 * AI 请求失败错误弹窗。
 * 接收 JSON 格式的错误数据，显示 HTTP 状态码、API 错误码及中文含义说明。
 *
 * 参数 (通过 setArguments / Bundle):
 *   errorData — JSON: {"httpCode":401, "apiCode":"1000", "apiMessage":"invalid api key"}
 */
public class ErrorDialogFragment extends DialogFragment {

    private static final Gson gson = new Gson();

    private int httpCode;
    private String apiCode;
    private String apiMessage;

    public ErrorDialogFragment() {}

    /**
     * 创建实例，通过 Bundle 传入错误数据 JSON
     */
    public static ErrorDialogFragment newInstance(String errorData) {
        ErrorDialogFragment fragment = new ErrorDialogFragment();
        Bundle args = new Bundle();
        args.putString("errorData", errorData);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        parseErrorData();
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        LayoutInflater inflater = requireActivity().getLayoutInflater();
        View view = inflater.inflate(R.layout.dialog_error, null);

        TextView tvHttpCode = view.findViewById(R.id.tvHttpCode);
        TextView tvApiError = view.findViewById(R.id.tvApiError);
        TextView tvErrorHint = view.findViewById(R.id.tvErrorHint);
        Button btnDismiss = view.findViewById(R.id.btnDismiss);

        // HTTP 状态码行
        tvHttpCode.setText("HTTP " + httpCode + " — " + getHttpDescription(httpCode));

        // API 错误码行
        if (!apiCode.isEmpty()) {
            String desc = getApiErrorDescription(apiCode);
            tvApiError.setText("错误码: " + apiCode + (desc.isEmpty() ? "" : " (" + desc + ")"));
            tvApiError.setVisibility(View.VISIBLE);
        } else {
            tvApiError.setVisibility(View.GONE);
        }

        // 显示原始 API 消息（如果有且与已显示的不同）
        if (!apiMessage.isEmpty() && !apiMessage.equals(getApiErrorDescription(apiCode))) {
            tvErrorHint.setText(apiMessage);
        }

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setView(view)
                .setCancelable(true)
                .create();

        btnDismiss.setOnClickListener(v -> dialog.dismiss());

        return dialog;
    }

    private void parseErrorData() {
        Bundle args = getArguments();
        if (args == null) return;
        String data = args.getString("errorData", "{}");
        try {
            JsonObject obj = gson.fromJson(data, JsonObject.class);
            httpCode = obj.has("httpCode") ? obj.get("httpCode").getAsInt() : 0;
            apiCode = obj.has("apiCode") ? obj.get("apiCode").getAsString() : "";
            apiMessage = obj.has("apiMessage") ? obj.get("apiMessage").getAsString() : "";
        } catch (Exception e) {
            httpCode = 0;
            apiCode = "";
            apiMessage = data; // 作为纯文本显示
        }
    }

    // ---- HTTP 状态码 → 中文说明 ----

    private static String getHttpDescription(int code) {
        switch (code) {
            case 400: return "请求参数有误";
            case 401: return "认证失败";
            case 403: return "无权限";
            case 404: return "接口不存在";
            case 429: return "请求过于频繁";
            case 500: return "服务器内部错误";
            case 502: return "网关错误";
            case 503: return "服务不可用";
            default:  return "请求失败";
        }
    }

    // ---- 常见 API 错误码 → 中文说明 ----

    private static String getApiErrorDescription(String code) {
        if (code == null || code.isEmpty()) return "";
        switch (code) {
            case "1000": return "API密钥无效、已过期、或使用了错误区域的密钥";
            case "1211": return "模型不存在或无权访问";
            case "1214": return "请求参数非法";
            case "1301": return "内容被安全过滤";
            case "1302":
            case "1305":
            case "1308": return "请求频率过高或配额不足";
            default:    return "";
        }
    }
}
