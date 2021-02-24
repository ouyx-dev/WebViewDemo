package com.kydw.webviewdemo.baidu_sougou

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.os.*
import androidx.appcompat.app.AppCompatActivity
import android.util.Log
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.widget.LinearLayout
import com.kydw.webviewdemo.CIRCLE_COUNT
import com.kydw.webviewdemo.KEYWORD_SITES
import com.kydw.webviewdemo.R
import com.kydw.webviewdemo.adapter.Model
import com.kydw.webviewdemo.baidu_simplify.MyTag
import com.kydw.webviewdemo.baidu_simplify.TAG
import com.kydw.webviewdemo.baidu_simplify.WebActivity
import com.kydw.webviewdemo.dialog.JAlertDialog
import com.kydw.webviewdemo.util.*
import com.kydw.webviewdemo.util.shellutil.CMD
import com.kydw.webviewdemo.util.shellutil.ShellUtils
import com.tencent.smtt.export.external.interfaces.SslError
import com.tencent.smtt.export.external.interfaces.SslErrorHandler
import com.tencent.smtt.sdk.*
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.*
import java.io.File
import java.lang.ref.WeakReference

class WebSouGouActivity : AppCompatActivity() {

    lateinit var webview: WebView
    var mCircleCount = 1
    var mCircleIndex = 1

    val isRoot = true
    private var mLoadingDbDialog: JAlertDialog? = null

    private val obj = InJavaScriptLocalObj(this)
    val sougouIndexUrl = "https://wap.sogou.com/"
    var testUrl="https://wap.sogou.com/web/searchList.jsp?keyword=钥匙机&suguuid=f2238415-fcc1-4b3b-83c2-a199012224e7&sugsuv=AAEcfEmDNAAAAAqHQGjNpQEA1wA&sugtime=1614054414104"



    val mKeyWords =
        mutableListOf<Pair<String, String>>()
    var mRequestIndex = 0

    fun indexNext() {
        mRequestIndex++
    }

    val handler = MyHandler(this)

    class MyHandler(activity: WebSouGouActivity) : Handler() {
        private val mActivity: WeakReference<WebSouGouActivity> = WeakReference(activity)

        override fun handleMessage(msg: Message) {
            if (mActivity.get() == null) {
                return
            }
            val activity = mActivity.get()
            when (msg.what) {
                0 -> {
                    //次 页not  found，或者满页
                    activity?.indexNext()
                    activity?.request()
                }
                1 -> {
                    //单个请求单次访问结束
                    activity?.indexNext()
                    activity?.request()
                }
                else -> {
                }
            }
        }
    }

    fun request() {
        if (mRequestIndex < mKeyWords.size) {
            //单次循环一个请求结束
            webview.loadUrl(sougouIndexUrl)
        } else {
            //一个循环结束
            if (mCircleCount == 0) {
                //无限循环
                nextCircle()
            } else {
                if (mCircleIndex == mCircleCount) {
                    //第mCircleIndex次循环结束
                    ToastUtil.show(this, "循环结束")
                    finish()
                } else {
                    //开启下一次循环
                    nextCircle()
                }
                mCircleIndex++
            }
        }
    }

    private fun nextCircle() {
        //切换IP
        if (mLoadingDbDialog == null) {
            mLoadingDbDialog =
                JAlertDialog.Builder(this).setContentView(R.layout.dialog_waitting_fly)
                    .setWidth_Height_dp(300, 120).setCancelable(isRoot)
                    .create()
        }
        mLoadingDbDialog?.show()

        // switchIP
        GlobalScope.launch(Dispatchers.IO) {
            val result0 = ShellUtils.execCommand(CMD.IP + " rmnet_data0", isRoot)
            if (result0?.successMsg != null) {
                val sucMsg0 = result0.successMsg!!
                Log.i(MyTag, "result0.sucMsg0=$sucMsg0, ")
                saveIP(sucMsg0)

            }

            ShellUtils.execCommand(CMD.AIRPLANE_MODE_ON, isRoot)
            delay(2000)
            ShellUtils.execCommand(CMD.AIRPLANE_MODE_OFF, isRoot)

            //关掉飞行时，4G 需要慢慢打开
            delay(2000)

            for (i in 1..60) {
                if (NetState.hasNetWorkConnection(this@WebSouGouActivity) && isOnline()) {
                    val result1 = ShellUtils.execCommand(CMD.IP + " rmnet_data0", isRoot)
                    if (result1?.successMsg != null) {
                        Log.i(MyTag, "result1.sucMsg=" + result1.successMsg?.toString())
                        appendFile(result1.successMsg + "\n\n",
                            getExternalFilesDir(null)!!.absolutePath + File.separator + "ip.txt",
                            this@WebSouGouActivity)
                    }
                    break
                } else {
                    Log.i(MyTag, "网络未建立，再等2秒,$i")
                    delay(2000)
                }
            }
            delay(2000)
            withContext(Dispatchers.Main) {
                mLoadingDbDialog?.dismiss()
                mRequestIndex = 0
                clearCache()
                webview.loadUrl(sougouIndexUrl)
            }
        }
    }

    private fun saveIP(sucMsg0: String) {
        appendFile(sucMsg0,
            getExternalFilesDir(null)!!.absolutePath + File.separator + "ip.txt", this)

    }

    override fun onDestroy() {
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        destroyWebView()
        super.onDestroy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val list = intent.getParcelableArrayExtra(KEYWORD_SITES)
        mCircleCount = intent.getIntExtra(CIRCLE_COUNT, 0)

        list?.forEach {
            Log.i(MyTag, it.toString())
            val model = it as Model
            mKeyWords.add(Pair(model.keyword!!, model.site!!))
        }
        webview = WebView(applicationContext)
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT)
        webview.layoutParams = lp
        content.addView(webview)

        webview.webViewClient = object : WebViewClient() {
//            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
//                if (url == null || url.startsWith("http://") || url.startsWith("https://")) {
//                    return false
//                } else try {
//                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
//                    view!!.context.startActivity(intent)
//                    return true
//                } catch (e: Exception) {
//                    Log.i(com.kydw.webviewdemo.baidu_simplify.TAG,
//                        "shouldOverrideUrlLoading Exception:$e")
//                    return true
//                }
//            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                Log.i(com.kydw.webviewdemo.baidu_simplify.TAG, "onPageStarted = $url")
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                Log.i(MyTag, "onPageFinished url= $url")
                Log.i(MyTag, "onPageFinished title= " + view?.title)
//                view!!.loadUrl(
//                    "javascript:" + "var url=\"${url!!}\";" +
//                            "window.java_obj.showSource("
//                            + "document.getElementsByTagName('html')[0].innerHTML,url);"
//                )
                val keyWord = mKeyWords[mRequestIndex].first
                val siteInfo = mKeyWords[mRequestIndex].second

                if (url == sougouIndexUrl) {
                    //首页，提交表单
                    Log.i(MyTag, "keyword$keyWord")
                    Log.i(MyTag, "siteInfo$siteInfo")
                    //首页，提交表单
                    val jsForm =
                        application.assets.open("sougou/js_index.js").bufferedReader().use {
                            it.readText()
                        }
                    Log.i(MyTag, "keyword$keyWord")
                    Log.i(MyTag, "siteInfo$siteInfo")
                    val head = "var keyword=\"$keyWord\";"
                    view!!.loadUrl("javascript:$head$jsForm")
//                    GlobalScope.launch {
//                        delay(200)
//                        val result = ShellUtils.execTap(880, 585)
//                        Log.i(MyTag, "tap result==" + result.toString())
//                    }

                }else if(url!!.startsWith("https://wap.sogou.com/web/searchList.jsp")){
                    Log.e("oyx","url="+url);
                    val jsToClickNext=application.assets.open("sougou/js_next_page.js").bufferedReader().use {
                        it.readText()
                    }
                    view!!.loadUrl("javascript:$jsToClickNext")
                }
//
//                else if (url.contains(siteInfo)) {
//                    Log.e(com.kydw.webviewdemo.baidu_simplify.TAG, "目标页加载成功=$url")
//                    val jsLook = application.assets.open("js_look.js").bufferedReader().use {
//                        it.readText()
//                    }
//                    view.loadUrl("javascript:$jsLook")
//                } else if (url.contains("baidu.com")) {
//                    Log.e(com.kydw.webviewdemo.baidu_simplify.TAG, "百度搜索页面=$url")
//                    if (url.contains("wappass.baidu.com/static/captcha/tuxing")) {
//                        //验证码
//                        Log.e(MyTag, "发现验证码界面" + url)
//                        val jsSwipe =
//                            application.assets.open("js_swipe_vc_by_cb.js").bufferedReader().use {
//                                it.readText()
//                            }
//                        view.loadUrl("javascript:$jsSwipe")
//                    } else {
//                        Log.e(MyTag, "发现下一页" + url)
//                        //Next 页
//                        val jsToNext =
//                            application.assets.open("js_to_next.js").bufferedReader().use {
//                                it.readText()
//                            }
//                        val head = "var targetSite = \"$siteInfo\";"
//                        view.loadUrl("javascript:$head$jsToNext")
//                    }
//                }
                super.onPageFinished(view, url)
            }

            override fun onReceivedSslError(
                view: WebView?,
                handler: SslErrorHandler?,
                error: SslError?
            ) {
                // let's ignore ssl error
                handler!!.proceed()
            }
        }
        webview.webChromeClient = WebChromeClient()

        webview.addJavascriptInterface(obj, "java_obj")
        setWebView(webview)
        request()
        webview.keepScreenOn = true
    }

    override fun onResume() {
        super.onResume()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }


    @SuppressLint("ClickableViewAccessibility", "SetJavaScriptEnabled")
    private fun setWebView(wv: WebView) {
        wv.setOnTouchListener { _, event ->
            if (event?.action == MotionEvent.ACTION_UP) {
                wv.requestDisallowInterceptTouchEvent(false)
            } else {
                wv.requestDisallowInterceptTouchEvent(true)
            }
            false
        }
        val webSettings = wv.settings

        //设置true,才能让Webivew支持<meta>标签的viewport属性,可任意比例缩放
        webSettings.useWideViewPort = true

        webSettings.loadWithOverviewMode = true
        //设置可以手势支持缩放
        webSettings.setSupportZoom(false)

        webSettings.builtInZoomControls = true
        //设定缩放控件隐藏
        webSettings.displayZoomControls = true

//      <meta content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=0" name="viewport"/>百度不支持缩放
//        wv.setInitialScale(100)

// 设置是否开启DOM存储API权限，默认false，未开启，设置为true，WebView能够使用DOM storage API
        webSettings.domStorageEnabled = true

        webSettings.javaScriptEnabled = true

//        webSettings.userAgentString = "User-Agent:Android"

        webSettings.userAgentString =
"Mozilla/5.0 (Linux; Android 9.0; 4G Build/MRA58K; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/56.0.2924.116 Mobile Safari/537.36 SogouMSE,SogouMobileBrowser/5.17.73"
    }

    override fun onBackPressed() {
        if (webview.canGoBack()) {
            webview.goBack()
        } else {
            super.onBackPressed()
        }
    }

    private fun destroyWebView() {
        webview.loadDataWithBaseURL(null, "", "text/html", "utf-8", null)
        webview.clearHistory()
        content.removeView(webview)
        webview.removeView(webview)
        webview.destroy()
    }

    private fun clearCache() {
        webview.clearCache(true)
        webview.clearHistory()
        clearCookies(this)
    }

    @SuppressWarnings("deprecation")
    fun clearCookies(context: Context?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
        } else if (context != null) {
            val cookieSyncManager = CookieSyncManager.createInstance(context)
            cookieSyncManager.startSync()
            val cookieManager: CookieManager = CookieManager.getInstance()
            cookieManager.removeAllCookie()
            cookieManager.removeSessionCookie()
            cookieSyncManager.stopSync()
            cookieSyncManager.sync()
        }
    }
}

private class InJavaScriptLocalObj(val context: Context) {
    @JavascriptInterface
    fun showSource(html: String, url: String) {
        Log.i(MyTag, "showSource    "+context.getExternalFilesDir(null)!!.absolutePath + File.separator + "htmls_sougou_browser.txt")
        appendFile(
            "\n" + "url=" + url + "\n" + html + "\n",
            context.getExternalFilesDir(null)!!.absolutePath + File.separator + "htmls_sougou_browser.txt",
            context
        )
    }

    @JavascriptInterface
    fun saveLog(content: String) {
        Log.i(TAG,
            "saveLog" + context.getExternalFilesDir(null)!!.absolutePath + File.separator + "baidu_dianji_browser.txt")
        if (PermissionUtil.hasRequiredPermissions(context))
            appendFile(
                content,
                context.getExternalFilesDir(null)!!.absolutePath + File.separator + "baidu_dianji_browser.txt",
                context
            )
    }

    @JavascriptInterface
    fun requestFinished() {
        Log.i(TAG, "requestFinished" + (Looper.myLooper() == Looper.getMainLooper()))
        GlobalScope.launch(Dispatchers.Main) {
            // 40页都找不到，下一页异常
            (context as WebActivity).handler.sendEmptyMessage(0)
        }
    }

    @JavascriptInterface
    fun finish() {
        Log.i(TAG, "finish")
        GlobalScope.launch(Dispatchers.Main) {
            // 目标网页跳转成功
            (context as WebSouGouActivity).handler.sendEmptyMessage(1)
        }
    }

    @JavascriptInterface
    fun swipe() {
        Log.i(TAG, "swipe")
        val x0 = 240
        val y0 = 1230
        val x1 = 870
        val y1 = 1230
        Log.i(MyTag, "$x0,$y0;$x1,$y1")
        GlobalScope.launch {
            val result = ShellUtils.execSwipe(x0, y0, x1, y1, 500)
            Log.i(MyTag, result.toString())
        }
    }

}