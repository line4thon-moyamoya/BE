package com.moyamoya.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.moyamoya.domain.auth.dto.request.ChildSignupRequest;
import com.moyamoya.domain.auth.dto.request.ConsentAgreementRequest;
import com.moyamoya.domain.auth.dto.request.LoginRequest;
import com.moyamoya.domain.auth.dto.request.SignupRequest;
import com.moyamoya.domain.auth.dto.response.LoginResponse;
import com.moyamoya.domain.auth.dto.response.SignupResponse;
import com.moyamoya.domain.auth.exception.AuthErrorCode;
import com.moyamoya.domain.child.entity.Child;
import com.moyamoya.domain.child.exception.ChildErrorCode;
import com.moyamoya.domain.child.repository.ChildRepository;
import com.moyamoya.domain.consent.entity.Consent;
import com.moyamoya.domain.consent.entity.ConsentType;
import com.moyamoya.domain.consent.exception.ConsentErrorCode;
import com.moyamoya.domain.consent.repository.ConsentRecordRepository;
import com.moyamoya.domain.consent.repository.ConsentRepository;
import com.moyamoya.domain.user.entity.User;
import com.moyamoya.domain.user.repository.UserRepository;
import com.moyamoya.global.exception.CustomException;
import com.moyamoya.security.IssuedTokens;
import com.moyamoya.security.JwtTokenProvider;
import com.moyamoya.security.TokenHasher;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private ChildRepository childRepository;

    @Mock
    private ConsentRepository consentRepository;

    @Mock
    private ConsentRecordRepository consentRecordRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private TokenHasher tokenHasher;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                userRepository,
                childRepository,
                consentRepository,
                consentRecordRepository,
                passwordEncoder,
                jwtTokenProvider,
                tokenHasher
        );
    }

    @Test
    void signupSavesAllDataAndReturnsTokens() {
        SignupRequest request = validRequest();
        User savedUser = mock(User.class);
        Child savedChild = mock(Child.class);
        List<Consent> consents = consentEntities();

        when(userRepository.existsByEmail("jiwoo.mom@email.com")).thenReturn(false);
        when(consentRepository.findAllById(any())).thenReturn(consents);
        when(passwordEncoder.encode(anyString())).thenAnswer(invocation -> "encoded-" + invocation.getArgument(0));
        when(userRepository.saveAndFlush(any(User.class))).thenReturn(savedUser);
        when(savedUser.getId()).thenReturn(1L);
        when(childRepository.save(any(Child.class))).thenReturn(savedChild);
        when(savedChild.getId()).thenReturn(2L);
        when(jwtTokenProvider.issueTokens(1L)).thenReturn(new IssuedTokens("access-token", "refresh-token"));
        when(tokenHasher.hash("refresh-token")).thenReturn("refresh-token-hash");

        SignupResponse response = authService.signup(request);

        assertThat(response.userId()).isEqualTo(1L);
        assertThat(response.childId()).isEqualTo(2L);
        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        verify(consentRecordRepository).saveAll(anyList());
        verify(savedUser).updateLastSelectedChildId(2L);
        verify(savedUser).updateRefreshToken("refresh-token-hash");
    }

    @Test
    void signupRejectsDuplicateEmail() {
        SignupRequest request = validRequest();
        when(userRepository.existsByEmail("jiwoo.mom@email.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.signup(request))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(AuthErrorCode.DUPLICATE_EMAIL);

        verify(userRepository, never()).saveAndFlush(any(User.class));
    }

    @Test
    void signupRejectsUnknownConsent() {
        SignupRequest request = validRequest();
        List<Consent> consents = consentEntities();
        consents.remove(consents.size() - 1);

        when(userRepository.existsByEmail("jiwoo.mom@email.com")).thenReturn(false);
        when(consentRepository.findAllById(any())).thenReturn(consents);

        assertThatThrownBy(() -> authService.signup(request))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ConsentErrorCode.CONSENT_NOT_FOUND);
    }

    @Test
    void signupRejectsRequiredConsentNotAgreed() {
        SignupRequest request = requestWithFourthConsent(false);
        List<Consent> consents = consentEntities();
        when(userRepository.existsByEmail("jiwoo.mom@email.com")).thenReturn(false);
        when(consentRepository.findAllById(any())).thenReturn(consents);

        assertThatThrownBy(() -> authService.signup(request))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ConsentErrorCode.REQUIRED_CONSENT_NOT_AGREED);
    }

    @Test
    void signupRejectsMissingRequiredConsents() {
        SignupRequest request = new SignupRequest(
                "jiwoo.mom@email.com",
                "password123!",
                "1234",
                new ChildSignupRequest("지우", "AGE_3_4"),
                List.of(new ConsentAgreementRequest(1L, true))
        );
        List<Consent> consents = List.of(consentEntities().get(0));
        when(userRepository.existsByEmail("jiwoo.mom@email.com")).thenReturn(false);
        when(consentRepository.findAllById(any())).thenReturn(consents);

        assertThatThrownBy(() -> authService.signup(request))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ConsentErrorCode.REQUIRED_CONSENT_NOT_AGREED);
    }

    @Test
    void signupRejectsInvalidAgeGroup() {
        SignupRequest request = new SignupRequest(
                "jiwoo.mom@email.com",
                "password123!",
                "1234",
                new ChildSignupRequest("지우", "AGE_6"),
                validAgreements(true)
        );

        assertThatThrownBy(() -> authService.signup(request))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ChildErrorCode.INVALID_AGE_GROUP);
    }

    @Test
    void loginAuthenticatesPasswordAndRotatesRefreshToken() {
        LoginRequest request = new LoginRequest("JIWOO.MOM@EMAIL.COM", "password123!");
        User user = mock(User.class);
        when(userRepository.findByEmail("jiwoo.mom@email.com")).thenReturn(Optional.of(user));
        when(user.getId()).thenReturn(1L);
        when(user.getLastSelectedChildId()).thenReturn(2L);
        when(user.getPassword()).thenReturn("encoded-password");
        when(passwordEncoder.matches("password123!", "encoded-password")).thenReturn(true);
        when(jwtTokenProvider.issueTokens(1L))
                .thenReturn(new IssuedTokens("access-token", "refresh-token"));
        when(tokenHasher.hash("refresh-token")).thenReturn("refresh-token-hash");

        LoginResponse response = authService.login(request);

        assertThat(response.userId()).isEqualTo(1L);
        assertThat(response.selectedChildId()).isEqualTo(2L);
        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        verify(user).updateRefreshToken("refresh-token-hash");
    }

    @Test
    void loginSelectsFirstChildWhenThereIsNoSelectionHistory() {
        LoginRequest request = new LoginRequest("jiwoo.mom@email.com", "password123!");
        User user = mock(User.class);
        Child firstChild = mock(Child.class);
        when(userRepository.findByEmail("jiwoo.mom@email.com")).thenReturn(Optional.of(user));
        when(user.getId()).thenReturn(1L);
        when(user.getPassword()).thenReturn("encoded-password");
        when(user.getLastSelectedChildId()).thenReturn(null);
        when(passwordEncoder.matches("password123!", "encoded-password")).thenReturn(true);
        when(childRepository.findFirstByUserOrderByIdAsc(user)).thenReturn(Optional.of(firstChild));
        when(firstChild.getId()).thenReturn(3L);
        when(jwtTokenProvider.issueTokens(1L))
                .thenReturn(new IssuedTokens("access-token", "refresh-token"));
        when(tokenHasher.hash("refresh-token")).thenReturn("refresh-token-hash");

        LoginResponse response = authService.login(request);

        assertThat(response.selectedChildId()).isEqualTo(3L);
        verify(user).updateLastSelectedChildId(3L);
    }

    @Test
    void loginRejectsMissingEmailOrPassword() {
        LoginRequest request = new LoginRequest("", "password123!");

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(AuthErrorCode.MISSING_LOGIN_CREDENTIALS);

        verify(userRepository, never()).findByEmail(anyString());
    }

    @Test
    void loginRejectsInvalidEmailFormat() {
        LoginRequest request = new LoginRequest("invalid-email", "password123!");

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(AuthErrorCode.INVALID_EMAIL);

        verify(userRepository, never()).findByEmail(anyString());
    }

    @Test
    void loginRejectsUnknownEmail() {
        LoginRequest request = new LoginRequest("unknown@email.com", "password123!");
        when(userRepository.findByEmail("unknown@email.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(AuthErrorCode.INVALID_CREDENTIALS);
    }

    @Test
    void loginRejectsWrongPasswordWithSameAuthenticationError() {
        LoginRequest request = new LoginRequest("jiwoo.mom@email.com", "wrong-password");
        User user = mock(User.class);
        when(userRepository.findByEmail("jiwoo.mom@email.com")).thenReturn(Optional.of(user));
        when(user.getPassword()).thenReturn("encoded-password");
        when(passwordEncoder.matches("wrong-password", "encoded-password")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(AuthErrorCode.INVALID_CREDENTIALS);

        verify(jwtTokenProvider, never()).issueTokens(any());
    }

    private SignupRequest validRequest() {
        return requestWithFourthConsent(true);
    }

    private SignupRequest requestWithFourthConsent(boolean agreed) {
        return new SignupRequest(
                "jiwoo.mom@email.com",
                "password123!",
                "1234",
                new ChildSignupRequest("지우", "AGE_3_4"),
                validAgreements(agreed)
        );
    }

    private List<ConsentAgreementRequest> validAgreements(boolean fourthConsentAgreed) {
        return List.of(
                new ConsentAgreementRequest(1L, true),
                new ConsentAgreementRequest(2L, true),
                new ConsentAgreementRequest(3L, true),
                new ConsentAgreementRequest(4L, fourthConsentAgreed),
                new ConsentAgreementRequest(5L, false)
        );
    }

    private List<Consent> consentEntities() {
        ConsentType[] types = ConsentType.values();
        List<Consent> consents = new ArrayList<>();

        for (int index = 0; index < types.length; index++) {
            Consent consent = mock(Consent.class);
            lenient().when(consent.getId()).thenReturn((long) index + 1);
            lenient().when(consent.getConsentType()).thenReturn(types[index]);
            consents.add(consent);
        }

        return consents;
    }
}
