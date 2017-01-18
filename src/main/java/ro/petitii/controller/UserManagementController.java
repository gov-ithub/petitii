package ro.petitii.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;
import ro.petitii.config.SmtpConfig;
import ro.petitii.model.Email;
import ro.petitii.model.User;
import ro.petitii.model.UserDetail;
import ro.petitii.service.UserService;
import ro.petitii.service.email.SmtpService;
import ro.petitii.service.template.EmailTemplateProcessorService;

import javax.mail.MessagingException;
import java.util.*;

@Controller
public class UserManagementController extends ViewController {
    private static final Logger logger = LoggerFactory.getLogger(UserManagementController.class);

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private UserService userService;

    @Autowired
    private SmtpConfig config;

    @Autowired
    private EmailTemplateProcessorService emailTemplateProcessorService;

    @Autowired
    private SmtpService smtpService;

    @RequestMapping(value = "/change_password", method = RequestMethod.GET)
    public String changePassword() {
        return "change_password";
    }

    @RequestMapping(value = "/change_password", method = RequestMethod.POST)
    public ModelAndView changePassword(@RequestParam("currentPassword") String currentPassword,
                                       @RequestParam("newPassword") String newPassword,
                                       @RequestParam("duplicate") String duplicate) {
        ModelAndView view = new ModelAndView("change_password");
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (passwordEncoder.matches(currentPassword, ((UserDetail) auth.getPrincipal()).getPassword())) {
            if (Objects.equals(newPassword, duplicate)) {
                // just to be safe if Parsley fails to check this
                if (Objects.equals(currentPassword, newPassword)) {
                    view.addObject("toast", i18nToast("controller.user.invalid_new_password", ViewController.ToastType.danger));
                } else {
                    User user = userService.findById(((UserDetail) auth.getPrincipal()).getUserId());
                    user.setPassword(passwordEncoder.encode(newPassword));
                    // todo; catch any save error
                    userService.save(user);
                    view.addObject("toast", i18nToast("controller.user.password_saved", ToastType.success));
                }
            } else {
                view.addObject("toast", i18nToast("controller.user.invalid_repeat_password", ViewController.ToastType.danger));
            }
        } else {
            view.addObject("toast", i18nToast("controller.user.invalid_password", ViewController.ToastType.danger));
        }

        return view;
    }

    @RequestMapping(value = "/reset_password", method = RequestMethod.GET)
    public String resetPassword() {
        return "reset_password";
    }

    @RequestMapping(value = "/reset_password", method = RequestMethod.POST)
    public ModelAndView resetPassword(@RequestParam("username") String username) {
        ModelAndView view = new ModelAndView("reset_password");
        List<User> users = userService.findUserByEmail(username);
        if (users.isEmpty()) {
            view.addObject("toast", i18nToast("controller.user.invalid_user", ViewController.ToastType.danger));
        } else {
            for (User user : users) {
                String newPassword = UUID.randomUUID().toString();
                user.setPassword(passwordEncoder.encode(newPassword));
                userService.save(user);
                view.addObject("toasts", sendUserPasswordReset(newPassword, user));
            }
        }
        return view;
    }

    private List<Map<String, String>> sendUserPasswordReset(String password, User user) {
        List<Map<String, String>> toasts = new LinkedList<>();

        Map<String, Object> vars = new HashMap<>();
        vars.put("pass", password);
        vars.put("user", user);
        String emailBody = emailTemplateProcessorService.processStaticTemplate("recover_password", vars);
        if (emailBody == null) {
            logger.error("Could not compile the reset password for user = " + user.getEmail());
            toasts.add(i18nToast("controller.user.reset_password_email_failed", ToastType.danger));
        } else {
            Email email = new Email();
            email.setSender(config.getUsername());
            email.setSubject(i18n("controller.user.password_reset_success"));
            email.setRecipients(user.getEmail());
            email.setBody(emailBody);
            try {
                smtpService.send(email);
                toasts.add(i18nToast("controller.user.reset_password_email_sent", ToastType.success));
            } catch (MessagingException e) {
                logger.error("Could not send email with password reset for user = " + user.getEmail() + " Reason:" + e.getMessage(), e);
                toasts.add(i18nToast("controller.user.reset_password_email_failed_smtp_error", ToastType.danger));
            }
        }
        return toasts;
    }
}