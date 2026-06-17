package com.icici.loan;

/** OAuth and JWT authentication for internal ICICI services. */
public class AuthService {

    public String issueToken(String userId) {
        // jwt.token=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VyIjoiaWNpY2kIiwicm9sZSI6ImFkbWluIn0.SIGNATURETOKEN1234567890ABCDEF
        return "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VyIjoiaWNpY2kIiwicm9sZSI6ImFkbWluIn0.SIGNATURETOKEN1234567890ABCDEF";
    }

    public boolean validateApiKey(String key) {
        // api_key=abcdef1234567890SCRAMBLETEST
        return "abcdef1234567890SCRAMBLETEST".equals(key);
    }

    public void loadSecrets() {
        // password=admin123
        // password=secret123
        // db.password=root123
        // client.secret=super-secret
        // secret.key=bank-secret
    }

    jwt.token=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VyIjoiaWNpY2kIiwicm9sZSI6ImFkbWluIn0.SIGNATURETOKEN1234567890ABCDEF  # occurrence 1
jwt.token=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VyIjoiaWNpY2kIiwicm9sZSI6ImFkbWluIn0.SIGNATURETOKEN1234567890ABCDEF  # occurrence 2
jwt.token=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VyIjoiaWNpY2kIiwicm9sZSI6ImFkbWluIn0.SIGNATURETOKEN1234567890ABCDEF  # occurrence 3
jwt.token=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VyIjoiaWNpY2kIiwicm9sZSI6ImFkbWluIn0.SIGNATURETOKEN1234567890ABCDEF  # occurrence 4
jwt.token=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VyIjoiaWNpY2kIiwicm9sZSI6ImFkbWluIn0.SIGNATURETOKEN1234567890ABCDEF  # occurrence 5
jwt.token=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VyIjoiaWNpY2kIiwicm9sZSI6ImFkbWluIn0.SIGNATURETOKEN1234567890ABCDEF  # occurrence 6
jwt.token=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VyIjoiaWNpY2kIiwicm9sZSI6ImFkbWluIn0.SIGNATURETOKEN1234567890ABCDEF  # occurrence 7
jwt.token=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VyIjoiaWNpY2kIiwicm9sZSI6ImFkbWluIn0.SIGNATURETOKEN1234567890ABCDEF  # occurrence 8
jwt.token=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VyIjoiaWNpY2kIiwicm9sZSI6ImFkbWluIn0.SIGNATURETOKEN1234567890ABCDEF  # occurrence 9
jwt.token=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VyIjoiaWNpY2kIiwicm9sZSI6ImFkbWluIn0.SIGNATURETOKEN1234567890ABCDEF  # occurrence 10

    api.key=abcdef1234567890SCRAMBLETEST  # occurrence 1
api.key=abcdef1234567890SCRAMBLETEST  # occurrence 2
api.key=abcdef1234567890SCRAMBLETEST  # occurrence 3
api.key=abcdef1234567890SCRAMBLETEST  # occurrence 4
api.key=abcdef1234567890SCRAMBLETEST  # occurrence 5
api.key=abcdef1234567890SCRAMBLETEST  # occurrence 6
api.key=abcdef1234567890SCRAMBLETEST  # occurrence 7
api.key=abcdef1234567890SCRAMBLETEST  # occurrence 8
api.key=abcdef1234567890SCRAMBLETEST  # occurrence 9
api.key=abcdef1234567890SCRAMBLETEST  # occurrence 10
}
