/*
 * Copyright (c) 2023. Tony Finnerty
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.tfcode.comparetout.util;

import android.content.Intent;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.webkit.WebViewAssetLoader;

public class LocalContentWebViewClient extends WebViewClient {

    private final WebViewAssetLoader mAssetLoader;

    public LocalContentWebViewClient(WebViewAssetLoader assetLoader) {
        mAssetLoader = assetLoader;
    }

    @Override
    public WebResourceResponse shouldInterceptRequest(WebView view,
                                                      WebResourceRequest request) {
        if (!(null == mAssetLoader)) return mAssetLoader.shouldInterceptRequest(request.getUrl());
        else return super.shouldInterceptRequest(view, request);
    }

    @Override
    public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
        if (request != null && (request.getUrl().getHost().startsWith("re.jrc.ec.europa.eu") ||
                request.getUrl().getHost().startsWith("joint-research-centre.ec.europa.eu") ||
                request.getUrl().getHost().startsWith("www.esbnetworks.ie") ||
                request.getUrl().getHost().startsWith("www.youtube.com") ||
                request.getUrl().getHost().startsWith("github.com") ||
                request.getUrl().getHost().startsWith("open.alphaess.com"))) {
            view.getContext().startActivity(
                    new Intent(Intent.ACTION_VIEW, request.getUrl()));
            return true;
        } else {
            return false;
        }
    }
}
