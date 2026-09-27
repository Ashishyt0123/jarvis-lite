# Jarvis Lite — Sirf Phone Se Banao (No PC)

## Naya kya hai is version mein
- 📷 **Photo lena** — "ye kya hai dekho" bolne par camera se photo lekar AI dekh ke batayega
- 📞 **Call karna** — "Mummy ko call karo" bolne par Contacts mein dhundh kar call lagayega
- 🧠 **Memory screen** — jo facts tum yaad rakhne ko bologe (jaise "remember mera birthday 5 March hai"), wo app band karne ke baad bhi yaad rahenge. App mein **Memory** button se dekh/delete kar sakte ho
- ⚙️ **Settings screen** — API key ab alag settings screen mein hai
- 🔔 **Background service** — ab app band karne ya screen off karne par bhi assistant sunta rahega (notification bar mein dikhega "Jarvis Lite - Listening")


PC nahi hai to koi baat nahi. Plan ye hai:
1. Tum ye code (jo already tumhare paas zip mein hai) apne phone se **GitHub** par bhejoge
2. GitHub ka "robot" (Actions) cloud mein khud APK bana dega
3. Tum wahan se APK download karke phone mein install kar loge

Sab **free** hai. Time lagega ~20-25 minute (zyadatar wait karna hai jab cloud build chalti hai).

---

## Step 1 — Termux install karo

Termux ek terminal app hai jisse phone se commands chala sakte ho.

⚠️ Play Store wala Termux purana ho chuka hai, **F-Droid wala lena hai**:
1. Browser mein jao: `f-droid.org`
2. F-Droid app download + install karo (ye ek app store hai, isse hi Termux milega)
3. F-Droid kholke "Termux" search karo, install karo

## Step 2 — Termux setup

Termux kholo aur ek-ek line type/paste karke Enter dabao:

```
termux-setup-storage
```
(Permission popup aayega, Allow karo — ye Termux ko tumhare Downloads folder tak access deta hai)

```
pkg update -y
pkg install git unzip -y
```

## Step 3 — Zip file nikaalo (extract)

Maan lo tumne `JarvisLite.zip` phone ke Downloads folder mein download kiya hai. Termux mein:

```
unzip ~/storage/downloads/JarvisLite.zip -d ~/
cd ~/JarvisLite
```

## Step 4 — GitHub account banao

1. Browser mein `github.com` kholo → Sign up → free account banao
2. Login karne ke baad, top-right **+** icon → **New repository**
3. Name do: `jarvis-lite`
4. **Public** ya **Private** — dono chalega (Private safer hai kyunki tumhara code kisi ko dikhega nahi)
5. ⚠️ "Add a README file" ka checkbox **mat** dabana — empty repo banana hai
6. **Create repository** dabao

## Step 5 — Access Token banao (password ki jagah)

GitHub ab normal password se push nahi karne deta, ek special "token" chahiye:

1. Browser mein: `github.com/settings/tokens`
2. **Generate new token** → **Generate new token (classic)**
3. Note mein kuch bhi likho jaise "phone"
4. Expiration: 90 days rakh lo
5. Neeche **repo** checkbox ON karo (poora box tick ho jaayega)
6. **Generate token** dabao
7. Jo token dikhega (ghggh_xxxxx jaisा kuch), usko **copy karke kahin safe jagah save kar lo** — ye dobara nahi dikhega!

## Step 6 — Code GitHub par bhejo (push)

Termux mein (`~/JarvisLite` folder ke andar), ek-ek line chalao:

```
git init
git add -A
git commit -m "first version"
git branch -M main
```

Ab apna GitHub username daal ke ye line chalao (USERNAME apna daalna):
```
git remote add origin https://github.com/USERNAME/jarvis-lite.git
git push -u origin main
```

Ye username aur password maangega:
- **Username:** tumhara GitHub username
- **Password:** yahan apna GitHub password NAHI — Step 5 wala **token** paste karo

Enter dabao. Thoda upload hoga (kuch MB hai), 1-2 minute lagega.

## Step 7 — Build hote dekho

1. Browser mein jao: `github.com/USERNAME/jarvis-lite/actions`
2. Ek build chal rahi dikhegi (yellow dot ⚫ → phir green check ✅), lagbhag 5-8 minute lagte hain
3. Jab green ho jaaye, us run pe tap karo
4. Neeche scroll karo → **Artifacts** section → **JarvisLite-apk** pe tap karke download karo (ye ek zip file degi jisme APK hoga)

## Step 8 — APK install karo

1. Downloaded zip ko phone ke file manager se **extract/unzip** karo (Files by Google app use kar sakte ho, ya jo bhi built-in file manager hai usme "Extract" ka option hota hai)
2. Andar `app-debug.apk` milega, usko tap karo
3. Agar "install blocked" dikhe → **Settings** kholega khud, wahan "Allow from this source" ON karo, wapas aakar install karo
4. App install ho jaayegi 🎉

---

## Ab app ke andar (Step 6 wale pehle guide jaisa hi):

1. Gemini API key paste karo (free milti hai `aistudio.google.com/apikey` se, Google account se login karke)
2. **Open Accessibility Settings** dabao → "Jarvis Lite" dhundo → ON karo
3. Mic permission allow karo
4. **Start** dabao aur bolna shuru karo

---

## Agli baar code update karna ho to

Agar main koi fix bhejunga ya tum khud kuch badloge, to sirf Step 6 wale commands dobara chalane honge (`git add -A`, `git commit -m "update"`, `git push`) — Step 1-5 dobara karne ki zaroorat nahi. Push hote hi GitHub khud naya APK bana dega.

Kahin bhi atko (error message ya screenshot), yahan paste kar dena — main dekh ke bata dunga.
