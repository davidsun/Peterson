/*******************************************************************************
 * Copyright (c) 2013 Zheng Sun.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 * 
 * Contributors:
 *     Zheng Sun - initial API and implementation
 ******************************************************************************/

package tv.huohua.peterson.social;

import java.io.IOException;

import tv.huohua.peterson.social.WeiboAuthorizer.AuthorizationListener;

import com.weibo.sdk.android.WeiboException;
import com.weibo.sdk.android.WeiboParameters;
import com.weibo.sdk.android.net.AsyncWeiboRunner;
import com.weibo.sdk.android.net.RequestListener;
import com.weibo.sdk.android.sso.SsoHandler;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

/**
 * @author Zheng Sun
 * 
 *         This class contains WeiboAuthorizer, that is, if current account is
 *         not authorized, the caller will call WeiboAuthorizer automatically.
 */

public class WeiboApiCaller {
    public interface OnApiCalledListener {
        void onApiCallFailed(Exception exception);

        void onApiCallSucceeded(String result);

        void onAuthorizationFailed();
    }

    class WeiboRequestListener implements RequestListener {
        private final OnApiCalledListener onApiCalledListener;

        public WeiboRequestListener(final OnApiCalledListener onApiCalledListener) {
            this.onApiCalledListener = onApiCalledListener;
        }

        public OnApiCalledListener getOnApiCalledListener() {
            return onApiCalledListener;
        }

        @Override
        public void onComplete(final String result) {
            if (onApiCalledListener != null) {
                final Message message = handler.obtainMessage(MSG_API_CALL_SUCCEEDED, onApiCalledListener);
                final Bundle bundle = new Bundle();
                bundle.putString(INTENT_KEY_RESULT, result);
                message.setData(bundle);
                handler.sendMessage(message);
            }
        }

        @Override
        public void onError(final WeiboException exception) {
            if (onApiCalledListener != null) {
                final Message message = handler.obtainMessage(MSG_API_CALL_FAILED, onApiCalledListener);
                final Bundle bundle = new Bundle();
                bundle.putSerializable(INTENT_KEY_EXCEPTION, exception);
                message.setData(bundle);
                handler.sendMessage(message);
            }
        }

        @Override
        public void onIOException(final IOException exception) {
            if (onApiCalledListener != null) {
                final Message message = handler.obtainMessage(MSG_API_CALL_FAILED, onApiCalledListener);
                final Bundle bundle = new Bundle();
                bundle.putSerializable(INTENT_KEY_EXCEPTION, exception);
                message.setData(bundle);
                handler.sendMessage(message);
            }
        }

    }

    static final private Handler handler = new Handler() {
        @Override
        public void handleMessage(final Message message) {
            if (message.obj != null && message.obj instanceof OnApiCalledListener) {
                final OnApiCalledListener listener = (OnApiCalledListener) message.obj;
                switch (message.what) {
                case MSG_API_CALL_FAILED:
                    final Exception exception = (Exception) message.getData().get(INTENT_KEY_EXCEPTION);
                    listener.onApiCallFailed(exception);
                    break;
                case MSG_API_CALL_SUCCEEDED:
                    final String result = (String) message.getData().get(INTENT_KEY_RESULT);
                    listener.onApiCallSucceeded(result);
                    break;
                case MSG_AUTORIZATION_FAILED:
                    listener.onAuthorizationFailed();
                    break;
                default:
                    break;
                }
            }
        }
    };

    static private final String INTENT_KEY_EXCEPTION = "exception";
    static private final String INTENT_KEY_RESULT = "result";

    static private final int MSG_API_CALL_FAILED = 0;
    static private final int MSG_API_CALL_SUCCEEDED = 1;
    static private final int MSG_AUTORIZATION_FAILED = 2;

    private final WeiboAuthorizer authorizer;

    public WeiboApiCaller(final Activity activity, final String consumerKey, final String redirectUrl) {
        this.authorizer = new WeiboAuthorizer(activity, consumerKey, redirectUrl);
    }

    public SsoHandler callApi(final String url, final String httpMethod, final WeiboParameters params,
            final OnApiCalledListener onApiCalledListener) {
        if (authorizer.isAuthed()) {
            params.add("access_token", authorizer.getAccessToken().getToken());
            AsyncWeiboRunner.request(url, params, httpMethod, new WeiboRequestListener(onApiCalledListener));
            return null;
        } else {
            authorizer.setAuthorizationListener(new AuthorizationListener() {
                @Override
                public void onAuthorizationCanceled(final WeiboAuthorizer authorizer) {
                    handler.sendMessage(handler.obtainMessage(MSG_AUTORIZATION_FAILED, onApiCalledListener));
                }

                @Override
                public void onAuthorizationError(final WeiboAuthorizer authorizer) {
                    handler.sendMessage(handler.obtainMessage(MSG_AUTORIZATION_FAILED, onApiCalledListener));
                }

                @Override
                public void onAuthorizationSucceeded(final WeiboAuthorizer authorizer) {
                    params.add("access_token", authorizer.getAccessToken().getToken());
                    AsyncWeiboRunner.request(url, params, httpMethod, new WeiboRequestListener(onApiCalledListener));
                }
            });
            return authorizer.startAuth();
        }
    }

    public WeiboAuthorizer getAuthorizer() {
        return authorizer;
    }
}
