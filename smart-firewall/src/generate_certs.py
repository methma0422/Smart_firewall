import datetime
from cryptography import x509
from cryptography.x509.oid import NameOID
from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives.asymmetric import rsa
from cryptography.hazmat.primitives import serialization
import os

def generate_self_signed_cert():
    # Generate RSA private key
    private_key = rsa.generate_private_key(
        public_exponent=65537,
        key_size=2048,
    )
    
    # Generate subject/issuer name
    subject = issuer = x509.Name([
        x509.NameAttribute(NameOID.COUNTRY_NAME, u"US"),
        x509.NameAttribute(NameOID.STATE_OR_PROVINCE_NAME, u"California"),
        x509.NameAttribute(NameOID.LOCALITY_NAME, u"San Francisco"),
        x509.NameAttribute(NameOID.ORGANIZATION_NAME, u"Smart Home Firewall"),
        x509.NameAttribute(NameOID.COMMON_NAME, u"localhost"),
    ])
    
    # Build certificate
    cert = x509.CertificateBuilder().subject_name(
        subject
    ).issuer_name(
        issuer
    ).public_key(
        private_key.public_key()
    ).serial_number(
        x509.random_serial_number()
    ).not_valid_before(
        datetime.datetime.utcnow() - datetime.timedelta(days=1)
    ).not_valid_after(
        # Valid for 10 years
        datetime.datetime.utcnow() + datetime.timedelta(days=3650)
    ).add_extension(
        x509.SubjectAlternativeName([x509.DNSName(u"localhost"), x509.DNSName(u"127.0.0.1")]),
        critical=False,
    ).sign(private_key, hashes.SHA256())
    
    # Paths
    import sys
    if getattr(sys, 'frozen', False):
        exe_dir = os.path.dirname(sys.executable)
    else:
        exe_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
        
    key_path = os.path.join(exe_dir, "ssl_key.pem")
    cert_path = os.path.join(exe_dir, "ssl_cert.pem")
    
    # Write Private Key
    with open(key_path, "wb") as f:
        f.write(private_key.private_bytes(
            encoding=serialization.Encoding.PEM,
            format=serialization.PrivateFormat.TraditionalOpenSSL,
            encryption_algorithm=serialization.NoEncryption()
        ))
        
    # Write Certificate
    with open(cert_path, "wb") as f:
        f.write(cert.public_bytes(serialization.Encoding.PEM))
        
    print(f"Self-signed certificate generated successfully:")
    print(f"  Key:  {key_path}")
    print(f"  Cert: {cert_path}")

if __name__ == "__main__":
    generate_self_signed_cert()
