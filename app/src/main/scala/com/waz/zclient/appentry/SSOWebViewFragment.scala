/**
 * Wire
 * Copyright (C) 2018 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.waz.zclient.appentry

import android.os.Bundle
import android.support.v7.widget.Toolbar
import android.view.View.OnClickListener
import android.view.{LayoutInflater, View, ViewGroup}
import android.webkit.WebView
import android.widget.TextView
import com.waz.ZLog
import com.waz.api.impl.ErrorResponse
import com.waz.service.{AccountsService, ZMessaging}
import com.waz.threading.Threading
import com.waz.utils._
import com.waz.zclient.appentry.SSOWebViewFragment._
import com.waz.zclient.utils.ViewUtils
import com.waz.zclient.{FragmentHelper, R}

import scala.concurrent.Future

class SSOWebViewFragment extends FragmentHelper {

  private lazy val accountsService    = inject[AccountsService]

  private lazy val webView = view[WebView](R.id.web_view)

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View = {
    inflater.inflate(R.layout.fragment_sso_webview, container, false)
  }

  override def onViewCreated(view: View, savedInstanceState: Bundle): Unit = {
    val toolbar = view.findViewById[Toolbar](R.id.toolbar)

    val title = view.findViewById[TextView](R.id.title)

    val code = getArguments.getString(SSOCode)

    webView.foreach { webView =>
      val webViewWrapper = new SSOWebViewWrapper(webView, ZMessaging.currentGlobal.backend.baseUrl.toString)
      webViewWrapper.onTitleChanged.onUi(title.setText(_))
      import Threading.Implicits.Ui

      webViewWrapper.loginWithCode(code).flatMap {
        case Left((cookie, userId)) =>
          //TODO: Move this to SE
          for {
            _  <- accountsService.addAccountEntryNoCredentials(userId, cookie)
            am <- accountsService.createAccountManager(userId, None, isLogin = Some(true))
            _  <- accountsService.setAccount(Some(userId))
            _  <- am.fold2(Future.successful(Left(ErrorResponse.internalError(""))), _.getOrRegisterClient())
          } yield getActivity.asInstanceOf[AppEntryActivity].onEnterApplication(false)
        case Right(error) =>
          ViewUtils.showAlertDialog(getActivity, "fuck", "shit", "ok", null, true)
          Future.successful({})
      }
    }

    toolbar.setNavigationIcon(R.drawable.action_back_dark)
    toolbar.setNavigationOnClickListener(new OnClickListener {
      override def onClick(v: View): Unit = onBackPressed()
    })

  }

  override def onBackPressed(): Boolean = {
    if (webView.map(_.canGoBack).getOrElse(false))
      webView.foreach(_.goBack())
    else
      getFragmentManager.popBackStack()
    true
  }

}

object SSOWebViewFragment {
  val Tag: String = ZLog.ImplicitTag.implicitLogTag

  val SSOCode = "SSO_CODE"

  def InitiateLoginPath(code: String) =  s"/sso/initiate-login/$code"

  def newInstance(code: String): SSOWebViewFragment = {
    val bundle = new Bundle()
    bundle.putString(SSOCode, code)
    returning(new SSOWebViewFragment())(_.setArguments(bundle))
  }
}
