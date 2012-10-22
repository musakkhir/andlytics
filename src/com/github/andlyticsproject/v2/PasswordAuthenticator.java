package com.github.andlyticsproject.v2;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.CookieStore;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.cookie.Cookie;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;

import android.util.Log;

import com.github.andlyticsproject.exception.AuthenticationException;

public class PasswordAuthenticator extends BaseAuthenticator {

	private static final String TAG = PasswordAuthenticator.class.getSimpleName();

	private static final String LOGIN_PAGE_URL = "https://accounts.google.com/ServiceLogin?service=androiddeveloper";
	private static final String AUTHENTICATE_URL = "https://accounts.google.com/ServiceLoginAuth?service=androiddeveloper";
	private static final String DEV_CONSOLE_URL = "https://play.google.com/apps/publish/v2/";

	private DefaultHttpClient httpClient;
	private String emailAddress;
	private String password;

	public PasswordAuthenticator(DefaultHttpClient httpClient, String emailAddress, String password) {
		this.httpClient = httpClient;
		this.emailAddress = emailAddress;
		this.password = password;
	}

	// 1. Get GALX from https://accounts.google.com/ServiceLogin?
	// 2. Post along with auth info to https://accounts.google.com/ServiceLoginAuth
	// 3. Get redirected to https://play.google.com/apps/publish/v2/ on success
	// (all needed cookies are in HttpClient's jar at this point)

	@Override
	public AuthInfo authenticate() throws AuthenticationException {
		try {
			HttpGet get = new HttpGet(LOGIN_PAGE_URL);
			HttpResponse response = httpClient.execute(get);
			if (response.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
				throw new AuthenticationException("Auth error: " + response.getStatusLine());
			}

			String galxValue = null;
			CookieStore cookieStore = httpClient.getCookieStore();
			List<Cookie> cookies = cookieStore.getCookies();
			for (Cookie c : cookies) {
				if ("GALX".equals(c.getName())) {
					galxValue = c.getValue();
				}
			}
			Log.d(TAG, "GALX: " + galxValue);

			HttpPost post = new HttpPost(AUTHENTICATE_URL);
			List<NameValuePair> parameters = createAuthParameters(galxValue);
			UrlEncodedFormEntity formEntity = new UrlEncodedFormEntity(parameters, "UTF-8");
			post.setEntity(formEntity);

			response = httpClient.execute(post);
			if (response.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
				throw new AuthenticationException("Auth error: " + response.getStatusLine());
			}

			cookies = cookieStore.getCookies();
			String adCookie = findAdCookie(cookies);
			Log.d(TAG, "AD cookie " + adCookie);
			if (adCookie == null) {
				throw new AuthenticationException("Couldn't get AD cookie.");
			}

			String responseStr = EntityUtils.toString(response.getEntity());
			Log.d(TAG, "Response: " + responseStr);
			String developerAccountId = findDeveloperAccountId(responseStr);
			if (developerAccountId == null) {
				throw new AuthenticationException("Couldn't get developer account ID.");
			}

			String xsrfToken = findXsrfToken(responseStr);
			if (xsrfToken == null) {
				throw new AuthenticationException("Couldn't get XSRF token.");
			}

			AuthInfo result = new AuthInfo(adCookie, xsrfToken, developerAccountId);
			result.addCookies(cookies);

			return result;
		} catch (ClientProtocolException e) {
			throw new AuthenticationException(e);
		} catch (IOException e) {
			throw new AuthenticationException(e);
		}
	}

	private List<NameValuePair> createAuthParameters(String galxValue) {
		List<NameValuePair> result = new ArrayList<NameValuePair>();
		NameValuePair email = new BasicNameValuePair("Email", emailAddress);
		result.add(email);
		NameValuePair passwd = new BasicNameValuePair("Passwd", password);
		result.add(passwd);
		NameValuePair galx = new BasicNameValuePair("GALX", galxValue);
		result.add(galx);
		NameValuePair cont = new BasicNameValuePair("continue", DEV_CONSOLE_URL);
		result.add(cont);

		return result;
	}
}
