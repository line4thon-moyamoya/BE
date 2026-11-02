package com.moyamoya.domain.auth.service;

import com.moyamoya.domain.auth.dto.request.ConsentAgreementRequest;
import com.moyamoya.domain.auth.dto.request.LoginRequest;
import com.moyamoya.domain.auth.dto.request.SignupRequest;
import com.moyamoya.domain.auth.dto.response.LoginResponse;
import com.moyamoya.domain.auth.dto.response.SignupResponse;
import com.moyamoya.domain.auth.exception.AuthErrorCode;
import com.moyamoya.domain.child.entity.AgeGroup;
import com.moyamoya.domain.child.entity.Child;
import com.moyamoya.domain.child.exception.ChildErrorCode;
import com.moyamoya.domain.child.repository.ChildRepository;
import com.moyamoya.domain.consent.entity.Consent;
import com.moyamoya.domain.consent.entity.ConsentRecord;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)+$"
    );
    private static final Pattern PARENT_PIN_PATTERN = Pattern.compile("^\\d{4}$");

    private final UserRepository userRepository;
    private final ChildRepository childRepository;
    private final ConsentRepository consentRepository;
    private final ConsentRecordRepository consentRecordRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final TokenHasher tokenHasher;

    @Transactional
    public SignupResponse signup(SignupRequest request) {
        validateRequiredFields(request);

        String email = request.email().trim().toLowerCase(Locale.ROOT);
        validateEmail(email);
        validateParentPin(request.parentPin());
        AgeGroup ageGroup = parseAgeGroup(request.child().ageGroup());
        Map<Long, Boolean> agreements = mapAgreements(request.consents());

        if (userRepository.existsByEmail(email)) {
            throw new CustomException(AuthErrorCode.DUPLICATE_EMAIL);
        }

        List<Consent> consents = consentRepository.findAllById(agreements.keySet());
        validateConsents(consents, agreements);

        User user = saveUser(email, request.password(), request.parentPin());
        Child child = childRepository.save(
                Child.create(user, request.child().nickname().trim(), ageGroup)
        );
        user.updateLastSelectedChildId(child.getId());

        List<ConsentRecord> consentRecords = consents.stream()
                .map(consent -> ConsentRecord.create(
                        user,
                        consent,
                        agreements.get(consent.getId())
                ))
                .toList();
        consentRecordRepository.saveAll(consentRecords);

        IssuedTokens tokens = jwtTokenProvider.issueTokens(user.getId());
        user.updateRefreshToken(tokenHasher.hash(tokens.refreshToken()));

        return new SignupResponse(
                user.getId(),
                child.getId(),
                tokens.accessToken(),
                tokens.refreshToken()
        );
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        validateLoginRequest(request);

        String email = request.email().trim().toLowerCase(Locale.ROOT);
        validateEmail(email);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new CustomException(AuthErrorCode.INVALID_CREDENTIALS));

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new CustomException(AuthErrorCode.INVALID_CREDENTIALS);
        }

        IssuedTokens tokens = jwtTokenProvider.issueTokens(user.getId());
        user.updateRefreshToken(tokenHasher.hash(tokens.refreshToken()));
        Long selectedChildId = resolveSelectedChildId(user);

        return new LoginResponse(
                user.getId(),
                selectedChildId,
                tokens.accessToken(),
                tokens.refreshToken()
        );
    }

    private Long resolveSelectedChildId(User user) {
        if (user.getLastSelectedChildId() != null) {
            return user.getLastSelectedChildId();
        }

        Child firstChild = childRepository.findFirstByUserOrderByIdAsc(user)
                .orElseThrow(() -> new IllegalStateException("로그인 사용자의 아이 프로필이 없습니다."));
        user.updateLastSelectedChildId(firstChild.getId());
        return firstChild.getId();
    }

    private User saveUser(String email, String password, String parentPin) {
        User user = User.create(
                email,
                passwordEncoder.encode(password),
                passwordEncoder.encode(parentPin)
        );

        try {
            return userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            throw new CustomException(AuthErrorCode.DUPLICATE_EMAIL);
        }
    }

    private void validateRequiredFields(SignupRequest request) {
        if (request == null
                || isBlank(request.email())
                || isBlank(request.password())
                || isBlank(request.parentPin())
                || request.child() == null
                || isBlank(request.child().nickname())
                || request.consents() == null
                || request.consents().isEmpty()) {
            throw new CustomException(AuthErrorCode.INVALID_SIGNUP_REQUEST);
        }
    }

    private void validateLoginRequest(LoginRequest request) {
        if (request == null || isBlank(request.email()) || isBlank(request.password())) {
            throw new CustomException(AuthErrorCode.MISSING_LOGIN_CREDENTIALS);
        }
    }

    private void validateEmail(String email) {
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new CustomException(AuthErrorCode.INVALID_EMAIL);
        }
    }

    private void validateParentPin(String parentPin) {
        if (!PARENT_PIN_PATTERN.matcher(parentPin).matches()) {
            throw new CustomException(AuthErrorCode.INVALID_PARENT_PIN);
        }
    }

    private AgeGroup parseAgeGroup(String value) {
        if (isBlank(value)) {
            throw new CustomException(ChildErrorCode.INVALID_AGE_GROUP);
        }

        try {
            return AgeGroup.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new CustomException(ChildErrorCode.INVALID_AGE_GROUP);
        }
    }

    private Map<Long, Boolean> mapAgreements(List<ConsentAgreementRequest> requests) {
        Map<Long, Boolean> agreements = new HashMap<>();

        for (ConsentAgreementRequest request : requests) {
            if (request == null
                    || request.consentId() == null
                    || request.consentId() <= 0
                    || request.isAgreed() == null
                    || agreements.putIfAbsent(request.consentId(), request.isAgreed()) != null) {
                throw new CustomException(AuthErrorCode.INVALID_SIGNUP_REQUEST);
            }
        }

        return agreements;
    }

    private void validateConsents(List<Consent> consents, Map<Long, Boolean> agreements) {
        if (consents.size() != agreements.size()) {
            throw new CustomException(ConsentErrorCode.CONSENT_NOT_FOUND);
        }

        Set<ConsentType> agreedTypes = new HashSet<>();
        for (Consent consent : consents) {
            if (Boolean.TRUE.equals(agreements.get(consent.getId()))) {
                agreedTypes.add(consent.getConsentType());
            }
        }

        boolean allRequiredConsentsAgreed = java.util.Arrays.stream(ConsentType.values())
                .filter(ConsentType::isRequired)
                .allMatch(agreedTypes::contains);

        if (!allRequiredConsentsAgreed) {
            throw new CustomException(ConsentErrorCode.REQUIRED_CONSENT_NOT_AGREED);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
