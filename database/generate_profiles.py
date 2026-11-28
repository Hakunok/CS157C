"""Generate synthetic profiles for the imported Twitter users.

Input:  users.csv (repo root), one column: userId
Output: database/import/users_profiles.csv
          userId, username, name, email, bio, passwordHash
        database/demo_accounts.csv (gitignored, share privately)
          username, password  for DEMO_COUNT accounts that can log in

Only userId comes from the public dataset. Everything else is synthetic.
Names and bios are generated with a fixed seed, so every teammate who runs
this gets the same profiles. Passwords use the secrets module, so they are
different on every run and never predictable.

Password hashes use the same format as Neo4jAuthService:
    iterations:base64(salt):base64(PBKDF2-HMAC-SHA256 key, 256 bits)
Hashing at 210,000 iterations takes about 0.1 s each, so only the demo
accounts get a real password. The rest get an empty passwordHash, which
Neo4jAuthService.login() rejects, so nobody can log in as them and no
default password exists.

Run from the repo root:  python3 database/generate_profiles.py
"""

import base64
import csv
import hashlib
import random
import secrets
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
USERS_IN = ROOT / "users.csv"
PROFILES_OUT = ROOT / "database" / "import" / "users_profiles.csv"
DEMO_OUT = ROOT / "database" / "demo_accounts.csv"

SEED = 157
DEMO_COUNT = 20
ITERATIONS = 210_000  # must match Neo4jAuthService.ITERATIONS

FIRST = """Aiden Amara Ana Arjun Ava Bilal Camila Chen Chloe Daniel Diego Elena
Emily Ethan Fatima Gabriel Grace Hana Hassan Isabella Ivan Jamal Javier Jin
Julia Kai Kenji Layla Leo Liam Lucas Maya Mei Mohammed Nadia Noah Nora Olivia
Omar Priya Rafael Rania Ravi Rosa Ryan Sara Sofia Tariq Thomas Valeria Victor
Wei Yara Yusuf Zainab Zara Aliya Timur Dana Erlan""".split()

LAST = """Abbasi Adeyemi Ahmed Alvarez Anderson Bauer Chen Costa Cruz Dang Diaz
Dubois Evans Fernandez Garcia Gupta Haddad Hernandez Ito Jensen Johnson Kaur
Khan Kim Kowalski Lee Lopez Martin Mendoza Moreau Nakamura Nguyen Novak Okafor
Olsen Park Patel Perez Petrov Quinn Rahman Ramirez Reyes Rossi Sato Schmidt
Silva Singh Smith Suzuki Tanaka Torres Tran Walker Wang Wilson Yamamoto Young
Zhang Saliev Omarov""".split()

ROLES = ["Student", "Engineer", "Designer", "Writer", "Photographer",
         "Teacher", "Nurse", "Data analyst", "Musician", "Researcher",
         "Chef", "Developer", "Product manager", "Artist", "Coach"]
LIKES = ["coffee", "hiking", "open source", "football", "jazz", "chess",
         "cooking", "travel", "sci-fi books", "running", "photography",
         "board games", "cycling", "history", "gardening", "anime"]
PLACES = ["San Jose", "Austin", "Toronto", "London", "Berlin", "Tokyo",
          "Seoul", "Lagos", "Mumbai", "Madrid", "Chicago", "Sydney",
          "Almaty", "Istanbul", "Mexico City", "Lisbon"]


def hash_password(password: str) -> str:
    salt = secrets.token_bytes(16)
    key = hashlib.pbkdf2_hmac("sha256", password.encode(), salt,
                              ITERATIONS, dklen=32)
    return (f"{ITERATIONS}:{base64.b64encode(salt).decode()}:"
            f"{base64.b64encode(key).decode()}")


def make_bio(rng: random.Random) -> str:
    if rng.random() < 0.25:
        return ""  # some real profiles leave the bio empty
    a, b = rng.sample(LIKES, 2)
    return f"{rng.choice(ROLES)} in {rng.choice(PLACES)}. Into {a} and {b}."


def main() -> None:
    with USERS_IN.open(newline="") as f:
        ids = [row["userId"].strip() for row in csv.DictReader(f)
               if row["userId"].strip()]

    rng = random.Random(SEED)
    demo_ids = set(rng.sample(ids, DEMO_COUNT))
    demo_rows = []

    PROFILES_OUT.parent.mkdir(parents=True, exist_ok=True)
    with PROFILES_OUT.open("w", newline="") as f:
        out = csv.writer(f, lineterminator="\n")
        out.writerow(["userId", "username", "name", "email", "bio",
                      "passwordHash"])
        for uid in ids:
            username = f"user_{uid}"
            name = f"{rng.choice(FIRST)} {rng.choice(LAST)}"
            email = f"{username}@example.com"
            bio = make_bio(rng)
            password_hash = ""
            if uid in demo_ids:
                password = secrets.token_urlsafe(9)
                password_hash = hash_password(password)
                demo_rows.append((username, password))
            out.writerow([uid, username, name, email, bio, password_hash])

    with DEMO_OUT.open("w", newline="") as f:
        out = csv.writer(f, lineterminator="\n")
        out.writerow(["username", "password"])
        out.writerows(sorted(demo_rows))

    print(f"{len(ids)} profiles -> {PROFILES_OUT}")
    print(f"{len(demo_rows)} demo logins -> {DEMO_OUT} (do not commit)")


if __name__ == "__main__":
    main()
