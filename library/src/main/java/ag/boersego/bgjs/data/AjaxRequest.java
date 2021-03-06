package ag.boersego.bgjs.data;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Locale;

import android.os.Build;
import android.util.Log;


public class AjaxRequest implements Runnable {

    private boolean mDebug;
    private V8UrlCache mCache;

    public interface AjaxListener {
		public void success(String data, int code, AjaxRequest request);

		public void error(String data, int code, Throwable tr, AjaxRequest request);
	}
	
	protected String mUrl;
	protected String mData;
	protected AjaxListener mCaller;
	protected Object mDataObject;
	private Object mAdditionalData;
	protected String mMethod = "GET";
	private StringBuilder mResultBuilder;
	protected String mOutputType;
	public String version;
	private String mReferer;
	
	public int connectionTimeout = 12000;
	private String mErrorData;
	private int mErrorCode;
	protected Exception mErrorThrowable;
	protected String mSuccessData;
	private int mSuccessCode;
	
	protected boolean mDoRunOnUiThread = true;
	
	private static String mUserAgent = null;
    private static final String TAG = "AjaxRequest";
	
	protected AjaxRequest() {
		
	}
	
	public void setUrl (String url) {
		mUrl = url;
	}

	public AjaxRequest(String targetURL, String data, AjaxListener caller) {
		if (data != null) {
			mUrl = targetURL + "?" + data;
		} else {
			mUrl = targetURL;
		}
		mData = data;
		mCaller = caller;
	}

	public AjaxRequest(String url, String data, AjaxListener caller,
			String method) {
		mData = data;
		mCaller = caller;
		if (method == null) {
			method = "GET";
		}
		if (method.equals("GET") && data != null) {
			mUrl = url + "?" + data;
		} else {
			mUrl = url;
		}
		mMethod  = method;
	}

    public void setCacheInstance (V8UrlCache cache) {
        mCache = cache;
    }

    public void doDebug (boolean debug) {
        mDebug = debug;
    }
	
	@SuppressWarnings("SameParameterValue")
    public void setOutputType(String type) {
		mOutputType = type;
	}
	
	public void addPostData(String key, String value) throws UnsupportedEncodingException
	{
	    if (mResultBuilder == null) {
	    	mResultBuilder = new StringBuilder();
	    } else {
	    	mResultBuilder.append("&");
	    }
	    

	    mResultBuilder.append(URLEncoder.encode(key, "UTF-8"));
	    mResultBuilder.append("=");
	    mResultBuilder.append(URLEncoder.encode(value, "UTF-8"));
	}

	@SuppressWarnings("ConstantConditions")
    public void run() {
		URL url;
		HttpURLConnection connection = null;
		try {
			// Create connection
			url = new URL(mUrl);
			connection = (HttpURLConnection) url.openConnection();
			connection.setInstanceFollowRedirects(true);
			connection.setConnectTimeout(connectionTimeout);
			connection.setReadTimeout(5000);
			Locale here = Locale.getDefault();
			
			if (AjaxRequest.mUserAgent == null) {
				AjaxRequest.mUserAgent = "myrmecophaga/" + version + " (Linux; U; Android " + Build.VERSION.RELEASE + "; " + here.getLanguage() + "-" + here.getCountry() + "; " + Build.MANUFACTURER + " " + Build.MODEL + " Build " + Build.DISPLAY + ") AppleWebKit/pi (KHTML, like Gecko) Version/4.0 Mobile Safari/beta";
			}
			connection.setRequestProperty("User-Agent", AjaxRequest.mUserAgent);
			if (mReferer != null) {
				connection.setRequestProperty("Referer", mReferer);
			}

            //noinspection ConstantConditions
            if (mDebug) {
				Log.d (TAG, "Opening URL " + mUrl);
				/* connection.setDefaultUseCaches(false);
				connection.setUseCaches(false); */
			} else {
				connection.setDefaultUseCaches(true);
				connection.setUseCaches(true);
			}
			connection.setDoInput(true);
			connection.setRequestMethod(mMethod);
			if (mResultBuilder != null) {
				mData = mResultBuilder.toString();
			}
			if (mData != null) {
				if (mOutputType != null) {
					connection.setRequestProperty("Content-Type",
						mOutputType);
				} else {
					if (mMethod.equals("POST")) {
						connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded ");
					}
				}
				byte[] outData = mData.getBytes();

				connection.setRequestProperty("Content-Length",
						Integer.toString(outData.length));
				connection.setRequestProperty("Content-Language", "en-US");
				connection.setDoOutput(true);
				// Send request
				OutputStream wr = connection.getOutputStream();
				wr.write(outData);
				if (mDebug) {
					Log.d (TAG, mMethod + " DATA " + mData);
				}
				wr.close();
			}

			// Get Response
			InputStream is = connection.getInputStream();
			BufferedReader rd = new BufferedReader(new InputStreamReader(is));
			String line;
			StringBuilder response = new StringBuilder(4096);
			
			// TODO: Optimize me
			while ((line = rd.readLine()) != null) {
				response.append(line);
				response.append('\r');
			}
			rd.close();
			final String responseStr = response.toString();
			
			try {
				String cc = connection.getHeaderField("Cache-Control");
				if (cc != null && mMethod.equals("GET")) {
					if (!cc.contains("no-store")) {
						// Response is cacheable
						if (cc.contains("max-age")) {
							int maxAge;
							int token = cc.indexOf("max-age=");
							int start = cc.indexOf("=", token) + 1;
							int end = cc.indexOf(",", start + 1);
							if (end != -1) {
								maxAge = Integer.parseInt(cc.substring(start, end));
							} else {
								maxAge = Integer.parseInt(cc.substring(start));
							}
							if (maxAge > 0 && mCache != null) {
								mCache.storeInCache(mUrl, responseStr, maxAge);
								if (mDebug) {
									Log.d (TAG, "Stored in cache for " + maxAge + " secs");
								}
							}
						}
					}
				}
			} catch (Exception ex) {
				Log.i (TAG, "Cannot set cache info", ex);
			}
			mSuccessData = responseStr;
			mSuccessCode = connection.getResponseCode();
			
			if (mDebug) {
				Log.d (TAG, "Response: " + mSuccessCode + "/" + responseStr);
			}
		} catch (Exception e) {

			String errDescription;
			try {
                if (connection != null) {
                    InputStream is = connection.getErrorStream();
                    if (is != null) {
                        BufferedReader rd = new BufferedReader(new InputStreamReader(is));
                        String line;
                        StringBuilder response = new StringBuilder(16384);

                        // TODO: Optimize me
                        while ((line = rd.readLine()) != null) {
                            response.append(line);
                            response.append('\r');
                        }
                        rd.close();
                        errDescription = response.toString();
                    } else {
                        if (connection != null) {
                            errDescription = connection.getResponseMessage();
                        } else {
                            errDescription = "connection is null";
                        }
                    }
                } else {
                    errDescription = "Connection is null";
                }
				Log.e (TAG, "Cannot load data from api: " + errDescription, e);
				mErrorData = errDescription;
                if (connection != null) {
				    mErrorCode = connection.getResponseCode();
                }
				mErrorThrowable = e;
			} catch (IOException ex) {
				Log.e (TAG, "Cannot get error info", ex);
				mErrorData = null;
				mErrorCode = 0;
				mErrorThrowable = ex;
			}

		} finally {
			if (connection != null) {
				connection.disconnect();
			}
		}
	}
	
	public void runCallback() {
		if (mSuccessData != null) {
			mCaller.success(mSuccessData, mSuccessCode, this);
			return;
		}
		mCaller.error(mErrorData, mErrorCode, mErrorThrowable, this);
	}

	public Object getDataObject() {
		return mDataObject;
	}
	
	public void setAdditionalData(Object dataObj) {
		mAdditionalData = dataObj;
	}
	
	public Object getAdditionalData() {
		return mAdditionalData;
	}
	
	public void setReferer(String referer) {
		mReferer = referer;
	}
	
	public String toString() {
		if (mUrl != null) {
			return mUrl;
		}
		
		return "AjaxRequest uninitialized";
	}

	public void doRunOnUiThread(@SuppressWarnings("SameParameterValue") boolean b) {
		mDoRunOnUiThread = b;
	}

}
