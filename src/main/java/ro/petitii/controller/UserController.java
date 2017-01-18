package ro.petitii.controller;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.mail.MessagingException;
import javax.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import ro.petitii.config.SmtpConfig;
import ro.petitii.model.Email;
import ro.petitii.model.User;
import ro.petitii.service.UserService;
import ro.petitii.service.email.SmtpService;
import ro.petitii.service.template.EmailTemplateProcessorService;

@Controller
@PreAuthorize("hasAuthority('ADMIN')")
public class UserController extends ViewController {
    private static final Logger LOGGER = LoggerFactory.getLogger(UserController.class);


    @Autowired
    private UserService userService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private SmtpConfig config;

    @Autowired
    private EmailTemplateProcessorService emailTemplateProcessorService;

    @Autowired
    private SmtpService smtpService;

    @RequestMapping("/users")
    public ModelAndView users() {
        ModelAndView modelAndView = new ModelAndView("users_list");
        modelAndView.addObject("page", "inbox");
        modelAndView.addObject("title", i18n("controller.user.title_users"));
        modelAndView.addObject("apiUrl", "/api/users");
        return modelAndView;
    }

    @RequestMapping(path = "/user/{id}/edit", method = RequestMethod.GET)
    public ModelAndView editUser(@PathVariable("id") Long id) {
        ModelAndView modelAndView = new ModelAndView("users_crud");
        //TODO: catch exceptions, add  error/success message
        User user = userService.findById(id);
        modelAndView.addObject("user", user);

        return modelAndView;
    }

    @RequestMapping(path = "/user/{id}/suspend", method = RequestMethod.GET)
    public ModelAndView removeUser(@PathVariable("id") Long id, final RedirectAttributes attr) {
        User user = userService.findById(id);
        user.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
        user.setRole(User.UserRole.SUSPENDED);
        //TODO: catch exceptions, add  error/success message
        userService.save(user);
        attr.addFlashAttribute("toast", i18nToast("controller.user.account_disabled", ToastType.success));
        return new ModelAndView("redirect:/users");
    }

    @RequestMapping(path = "/user/{id}/reset", method = RequestMethod.GET)
    public ModelAndView resetPasswordUser(@PathVariable("id") Long id, final RedirectAttributes attr) {
    	//TODO: catch exceptions, add  error/success message
        User user = userService.findById(id);
        String newPassword = UUID.randomUUID().toString();
        user.setPassword(passwordEncoder.encode(newPassword));
        //TODO: catch exceptions, add  error/success message
        userService.save(user);

        //TODO: catch exceptions, add  error/success message
        String subject = i18n("controller.user.password_reset_success");
        List<Map<String, String>> toasts = sendUserPasswordReset("reset_password", subject, newPassword, user);
        attr.addFlashAttribute("toasts", toasts);
        return new ModelAndView("redirect:/users");
    }


    @RequestMapping(path = "/user", method = RequestMethod.GET)
    public ModelAndView addUser() {
        User user = new User();
        user.setRole(User.UserRole.USER);

        ModelAndView modelAndView = new ModelAndView("users_crud");
        modelAndView.addObject("user", user);
        return modelAndView;
    }

    private String setNewPassword(User user) {
        String newPassword = null;
        if (user.getChangePassword()) {
            if (!StringUtils.isEmpty(user.getPassword())
                    && !StringUtils.isEmpty(user.getPasswordCopy()) &&
                    user.getPassword().equals(user.getPasswordCopy())) {
                newPassword = user.getPassword();
                user.setPassword(passwordEncoder.encode(user.getPassword()));
            } else {
                throw new RuntimeException("Password not provided");
            }
        } else {
            if (user.getId() == null) {
                //set some random temporary password
                newPassword = UUID.randomUUID().toString();
                user.setPassword(passwordEncoder.encode(newPassword));
            } else {
                //set the previous password
            	//TODO: catch exceptions, add  error/success message
                user.setPassword(userService.findById(user.getId()).getPassword());
            }
        }
        return newPassword;
    }

    @RequestMapping(path = "/user", method = RequestMethod.POST)
    public ModelAndView saveUser(@Valid User user, final RedirectAttributes attr) {
        boolean newUser = user.getId() == null;
        String newPassword = setNewPassword(user);

        //TODO: catch exceptions, add  error/success message
        List<User> existingUser = userService.findUserByEmail(user.getEmail());
        if (newUser && existingUser != null && !existingUser.isEmpty()) {
            attr.addFlashAttribute("toast", i18nToast("controller.user.email_address_exists", ToastType.danger));
        } else {
        	//TODO: catch exceptions, add  error/success message
            userService.save(user);
            if (newUser) {
                String subject = i18n("controller.user.welcome");
                List<Map<String, String>> toasts = sendUserPasswordReset("welcome_user", subject, newPassword, user);
                attr.addFlashAttribute("toasts", toasts);
            }
        }

        return new ModelAndView("redirect:/users");
    }

    private List<Map<String, String>> sendUserPasswordReset(String template, String subject, String password, User user) {
        List<Map<String, String>> toasts = new LinkedList<>();

        Map<String, Object> vars = new HashMap<>();
        vars.put("pass", password);
        vars.put("user", user);
        String emailBody = emailTemplateProcessorService.processStaticTemplate(template, vars);
        if (emailBody == null) {
            LOGGER.error("Could not compile the reset password for user = " + user.getEmail());
            toasts.add(i18nToast("controller.user.reset_password_email_failed", ToastType.danger));
        } else {
            Email email = new Email();
            email.setSender(config.getUsername());
            email.setSubject(subject);
            email.setRecipients(user.getEmail());
            email.setBody(emailBody);
            try {
                LOGGER.info("Sending reset password email" + emailBody);
                smtpService.send(email);
                toasts.add(i18nToast("controller.user.reset_password_email_sent", ToastType.success));
            } catch (MessagingException e) {
                LOGGER.error("Could not send email with password reset for user = " + user.getEmail(), e);
                toasts.add(i18nToast("controller.user.reset_password_email_failed_with_error" , ToastType.danger, e.getMessage()));
            }
        }
        return toasts;
    }
}
