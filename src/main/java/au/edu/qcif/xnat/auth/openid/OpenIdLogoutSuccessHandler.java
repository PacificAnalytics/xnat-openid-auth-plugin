package au.edu.qcif.xnat.auth.openid;

import lombok.extern.slf4j.Slf4j;
import org.nrg.xnat.security.XnatLogoutSuccessHandler;
import org.springframework.security.core.Authentication;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@Slf4j
public class OpenIdLogoutSuccessHandler extends XnatLogoutSuccessHandler {
    private final String _openOpenIDLogoutSuccessUrl;
    private final String _securedOpenIDLogoutSuccessUrl;

    private boolean _reqLogin;

    public OpenIdLogoutSuccessHandler(final boolean requireLogin, final String openXnatLogoutSuccessUrl, final String securedXnatLogoutSuccessUrl) {
        super(requireLogin, openXnatLogoutSuccessUrl, securedXnatLogoutSuccessUrl);
        _openOpenIDLogoutSuccessUrl = openXnatLogoutSuccessUrl;
        _securedOpenIDLogoutSuccessUrl = securedXnatLogoutSuccessUrl;
        _reqLogin = requireLogin;
    }

    @Override
    public void onLogoutSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {
        setDefaultTargetUrl(getReqLogoutSuccessUrl());
        super.handle(request, response, authentication);
    }

    @Override
    public void setRequireLogin(final boolean requireLogin) {
        super.setRequireLogin(requireLogin);
        _reqLogin = requireLogin;
    }

    private String getReqLogoutSuccessUrl() {
        String logoutUrl = OpenIdAuthPlugin.getLogoutUrl(_reqLogin);
        log.debug("OpenID logout handler. Setting required logout success URL to: {}", logoutUrl);
        return logoutUrl;

    }

}
