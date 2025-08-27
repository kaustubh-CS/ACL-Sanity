

# Rest-Assured Starter — Running tests through Burp (permanent setup)

This repo includes tests (e.g., `ACLMatrixTest`) that send HTTPS traffic through **Burp Suite** so you can inspect requests/responses. These notes capture the **one-time prerequisites** and the **exact steps** I followed so it works reliably on any machine.

---

## Prerequisites

- **Burp Suite** (Community or Pro)
- **Java JDK 16** (the JVM Maven uses) and **Maven 3.9+**
  - Check what Maven is using:
    ```bash
    mvn -v
    ```
    The `Java version` and path shown here is the JDK whose truststore we must modify.
- Ability to run `keytool` (bundled with the JDK)

> **Important**: The Burp CA must be imported into the **same JDK** used by Maven. If you switch or upgrade JDKs later, repeat the import step for the new JDK.

---

## One‑time setup (per machine)

### 1) Set `JAVA_HOME` and make it persistent (macOS zsh)
```bash
# Select the JDK version Maven should use (adjust -v if needed)
export JAVA_HOME=$(/usr/libexec/java_home -v 16)
export PATH="$JAVA_HOME/bin:$PATH"

# Persist for future shells
if ! grep -q "java_home -v 16" ~/.zshrc 2>/dev/null; then
  echo 'export JAVA_HOME=$(/usr/libexec/java_home -v 16)' >> ~/.zshrc
  echo 'export PATH="$JAVA_HOME/bin:$PATH"' >> ~/.zshrc
fi
```
Verify:
```bash
echo "$JAVA_HOME"
ls -l "$JAVA_HOME/bin/keytool"
```

### 2) Export Burp's CA certificate (DER)
- **Newer Burp**: *Settings → TLS → CA Certificate → Save certificate* (choose **DER** format).
- **Older Burp**: *Proxy → Options → Import / export CA certificate* (export as **DER**).
Save it as `~/burpCA.der`.

### 3) Import Burp CA into the JDK truststore (permanent)
```bash
sudo "$JAVA_HOME/bin/keytool" -importcert \
  -alias burp-suite \
  -file "$HOME/burpCA.der" \
  -keystore "$JAVA_HOME/lib/security/cacerts" \
  -storepass changeit \
  -noprompt
```
Verify the alias exists:
```bash
"$JAVA_HOME/bin/keytool" -list \
  -keystore "$JAVA_HOME/lib/security/cacerts" \
  -storepass changeit | grep -i burp
```

> If you need to re-import: delete then import again
> ```bash
> sudo "$JAVA_HOME/bin/keytool" -delete -alias burp-suite \
>   -keystore "$JAVA_HOME/lib/security/cacerts" -storepass changeit
> ```

---

## Run with Burp

1. Start **Burp Suite**
   - Ensure a listener on **127.0.0.1:8080**.
   - **Intercept: OFF** (we want to see traffic in *Proxy → HTTP history* without pausing tests).
2. Run the tests:
   ```bash
   mvn test -Dtest=ACLMatrixTest
   ```
   The test suite configures the proxy at `127.0.0.1:8080`. You should see the requests in **Burp → Proxy → HTTP history**.
3. Output artifacts: look for `target/acl-report.csv` after the run.

---

## Troubleshooting

### PKIX / SSLHandshake: `unable to find valid certification path` 
- You imported the CA into a *different* JDK than the one Maven uses. Run `mvn -v` and re-import the CA into that JDK’s `cacerts`.
- You upgraded/changed JDK: repeat the import for the new `$JAVA_HOME`.
- Use the debug flag if needed:
  ```bash
  MAVEN_OPTS="-Djavax.net.debug=ssl:handshake" mvn -X test -Dtest=ACLMatrixTest
  ```

### No traffic visible in Burp
- Burp listening on **127.0.0.1:8080** and **Intercept OFF**.
- Another proxy/VPN/corp SSL inspection might be interfering — ensure the test actually targets Burp (the logs show `[proxy] 127.0.0.1:8080`).
- Port conflicts: change Burp to a free port and update the test config accordingly (or set env vars if the tests support them).

### Remove the Burp CA from this JDK (cleanup)
```bash
sudo "$JAVA_HOME/bin/keytool" -delete -alias burp-suite \
  -keystore "$JAVA_HOME/lib/security/cacerts" -storepass changeit
```

---

## Notes
- Only import the Burp CA into **development** JDKs. Do **not** ship this truststore to production.
- If tests require API tokens, provide them via the expected env vars or config (the test will warn if missing).
