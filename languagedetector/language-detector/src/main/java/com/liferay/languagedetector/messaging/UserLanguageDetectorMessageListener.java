package com.liferay.languagedetector.messaging;

import com.liferay.languagedetector.util.Constants;
import com.liferay.languagedetector.util.PortletPropsValues;
import com.liferay.portal.NoSuchUserException;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.exception.SystemException;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.messaging.Message;
import com.liferay.portal.kernel.messaging.MessageListener;
import com.liferay.portal.kernel.messaging.MessageListenerException;
import com.liferay.portal.kernel.util.StringPool;
import com.liferay.portal.kernel.util.Validator;
import com.liferay.portal.model.User;
import com.liferay.portal.service.UserLocalServiceUtil;
import com.liferay.portal.util.PortalUtil;
import com.liferay.portlet.expando.model.ExpandoColumn;
import com.liferay.portlet.expando.model.ExpandoTable;
import com.liferay.portlet.expando.model.ExpandoValue;
import com.liferay.portlet.expando.service.ExpandoColumnLocalServiceUtil;
import com.liferay.portlet.expando.service.ExpandoTableLocalServiceUtil;
import com.liferay.portlet.expando.service.ExpandoValueLocalServiceUtil;
public class UserLanguageDetectorMessageListener implements MessageListener {

	@Override
	public void receive(Message message) throws MessageListenerException {
		long companyId = PortalUtil.getDefaultCompanyId();

		try {
			User user = UserLocalServiceUtil.getUserByEmailAddress(
				companyId, PortletPropsValues.DETECT_USER_EMAIL);

			String languageChangeTime = getLanguageChangeTime(user);

			recoverUserLanguage(user, languageChangeTime);
		}
		catch (NoSuchUserException nsue) {
			if (_log.isInfoEnabled()) {
				_log.info(
					"user " +
					PortletPropsValues.DETECT_USER_EMAIL +
					" does not exist");
			}
		}
		catch (Exception e) {
			if (_log.isErrorEnabled()) {
				_log.error(e);
			}
		}
	}

	protected String getLanguageChangeTime(User user)
		throws PortalException, SystemException {

		ExpandoTable table = ExpandoTableLocalServiceUtil.getDefaultTable(
				user.getCompanyId(), User.class.getName());

		ExpandoColumn column = ExpandoColumnLocalServiceUtil.getColumn(
			table.getTableId(), Constants.EXPANDO_COLUMN_NAME);

		ExpandoValue value = ExpandoValueLocalServiceUtil.getValue(
			table.getTableId(), column.getColumnId(), user.getUserId());

		if (value != null) {
			return value.getData();
		}

		return StringPool.BLANK;
	}

	protected void recoverUserLanguage(User user, String languageChangeTime)
		throws PortalException, SystemException {

		ExpandoTable table = ExpandoTableLocalServiceUtil.getDefaultTable(
			user.getCompanyId(), User.class.getName());

		ExpandoColumn column = ExpandoColumnLocalServiceUtil.getColumn(
			table.getTableId(), Constants.EXPANDO_COLUMN_NAME);

		String userLanguage = user.getLanguageId();

		long timeStamp = System.currentTimeMillis();

		if (Validator.isNull(languageChangeTime) &&
			userLanguage.equals(PortletPropsValues.DETECT_LANGUAGE)) {

			ExpandoValueLocalServiceUtil.addValue(
				table.getClassNameId(), table.getTableId(),
				column.getColumnId(), user.getUserId(),
				String.valueOf(timeStamp));

			if (_log.isInfoEnabled()) {
				_log.info("Detect user language change.");
			}
		}

		if (Validator.isNotNull(languageChangeTime) &&
			!userLanguage.equals(PortletPropsValues.DETECT_LANGUAGE)) {

			ExpandoValueLocalServiceUtil.addValue(
				table.getClassNameId(), table.getTableId(),
				column.getColumnId(), user.getUserId(), StringPool.BLANK);
		}

		if (Validator.isNotNull(languageChangeTime) &&
			userLanguage.equals(PortletPropsValues.DETECT_LANGUAGE) &&
			(timeStamp - Long.valueOf(languageChangeTime))
				>= PortletPropsValues.LANGUAGE_LIFE_MS) {

			user.setLanguageId(PortletPropsValues.RECOVER_LANGUAGE);
			UserLocalServiceUtil.updateUser(user);

			ExpandoValueLocalServiceUtil.addValue(
				table.getClassNameId(), table.getTableId(),
				column.getColumnId(), user.getUserId(), StringPool.BLANK);

			if (_log.isInfoEnabled()) {
				_log.info(
					"update user language to " +
					PortletPropsValues.RECOVER_LANGUAGE);
			}
		}
	}

	private static Log _log = LogFactoryUtil.getLog(
		UserLanguageDetectorMessageListener.class);
}