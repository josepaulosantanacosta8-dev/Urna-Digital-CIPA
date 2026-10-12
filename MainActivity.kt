package br.com.urnacipa

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.widget.*
import android.graphics.Color
import android.text.InputType
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("urna_cipa_local", MODE_PRIVATE) }
    private val candidates = mutableListOf<Candidate>()
    private val usedVoters = mutableSetOf<String>()
    private var electionOpen = true
    private val orange = Color.rgb(239, 108, 0)
    data class Candidate(val number: String, val name: String, val photo: String, var votes: Int)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadData()
        showHome()
    }

    private fun saveData() {
        val arr = JSONArray()
        candidates.forEach { arr.put(JSONObject().put("number", it.number).put("name", it.name).put("photo", it.photo).put("votes", it.votes)) }
        prefs.edit().putString("candidates", arr.toString())
            .putString("used", usedVoters.joinToString("\n"))
            .putBoolean("open", electionOpen).apply()
    }

    private fun loadData() {
        candidates.clear()
        try {
            val arr = JSONArray(prefs.getString("candidates", "[]"))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                candidates.add(Candidate(o.optString("number"), o.optString("name"), o.optString("photo"), o.optInt("votes")))
            }
        } catch (_: Exception) {}
        usedVoters.clear()
        prefs.getString("used", "")?.lines()?.filter { it.isNotBlank() }?.forEach { usedVoters.add(it) }
        electionOpen = prefs.getBoolean("open", true)
    }

    private fun layout(title: String): LinearLayout {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(22, 20, 22, 20)
            setBackgroundColor(Color.rgb(250, 250, 250))
        }
        val heading = TextView(this).apply {
            text = title
            textSize = 25f
            setTextColor(orange)
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 18)
        }
        root.addView(heading)
        setContentView(ScrollView(this).apply { addView(root) })
        return root
    }

    private fun button(root: LinearLayout, label: String, action: () -> Unit) {
        root.addView(Button(this).apply {
            text = label
            isAllCaps = false
            setOnClickListener { action() }
        }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 8 })
    }

    private fun field(hint: String, secret: Boolean = false): EditText = EditText(this).apply {
        this.hint = hint
        textSize = 16f
        if (secret) inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        else inputType = InputType.TYPE_CLASS_TEXT
    }

    private fun showHome() {
        val root = layout("URNA DIGITAL CIPA")
        val subtitle = TextView(this).apply {
            text = if (electionOpen) "Votação aberta" else "Votação encerrada"
            textSize = 18f; gravity = Gravity.CENTER; setPadding(0, 0, 0, 16)
        }
        root.addView(subtitle)
        button(root, "Votar") { showVoterCheck() }
        button(root, "Administrador") { showAdminLogin() }
        root.addView(TextView(this).apply {
            text = "Protótipo local: os dados ficam somente neste aparelho. Faça testes antes de qualquer eleição real."
            textSize = 13f; setTextColor(Color.DKGRAY); setPadding(0, 18, 0, 0)
        })
    }

    private fun showVoterCheck() {
        if (!electionOpen) { toast("A votação está encerrada."); return }
        if (candidates.isEmpty()) { toast("O administrador precisa cadastrar candidatos primeiro."); return }
        val root = layout("Identificação do eleitor")
        val id = field("Digite o identificador do eleitor")
        root.addView(id)
        button(root, "Continuar") {
            val voter = id.text.toString().trim()
            if (voter.length < 2) toast("Informe um identificador válido.")
            else if (usedVoters.contains(voter)) toast("Este identificador já votou.")
            else showBallot(voter)
        }
        button(root, "Voltar") { showHome() }
    }

    private fun showBallot(voter: String) {
        val root = layout("Escolha seu candidato")
        root.addView(TextView(this).apply { text = "Confira sua escolha antes de confirmar."; textSize = 15f })
        candidates.forEach { c ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(4, 8, 4, 8) }
            if (c.photo.isNotBlank()) {
                try {
                    val image = ImageView(this)
                    contentResolver.openInputStream(Uri.parse(c.photo))?.use { image.setImageBitmap(BitmapFactory.decodeStream(it)) }
                    image.layoutParams = LinearLayout.LayoutParams(100, 100)
                    image.scaleType = ImageView.ScaleType.CENTER_CROP
                    row.addView(image)
                } catch (_: Exception) {}
            }
            val info = TextView(this).apply {
                text = "  Nº ${c.number} — ${c.name}"
                textSize = 18f
            }
            row.addView(info, LinearLayout.LayoutParams(0, -2, 1f))
            val choose = Button(this).apply {
                text = "Votar"
                setOnClickListener {
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("Confirmar voto")
                        .setMessage("Confirmar voto em ${c.name}, número ${c.number}?")
                        .setNegativeButton("Cancelar", null)
                        .setPositiveButton("Confirmar") { _, _ ->
                            if (usedVoters.contains(voter)) toast("Identificador já utilizado.")
                            else {
                                c.votes += 1
                                usedVoters.add(voter)
                                saveData()
                                AlertDialog.Builder(this@MainActivity).setMessage("Voto registrado. Obrigado!").setPositiveButton("OK") { _, _ -> showHome() }.show()
                            }
                        }.show()
                }
            }
            row.addView(choose)
            root.addView(row)
            root.addView(View(this).apply { setBackgroundColor(Color.LTGRAY); layoutParams = LinearLayout.LayoutParams(-1, 1) })
        }
        button(root, "Cancelar") { showHome() }
    }

    private fun showAdminLogin() {
        val root = layout("Acesso do administrador")
        root.addView(TextView(this).apply {
            text = "Senha inicial: 1234. Altere-a no painel após entrar."
            textSize = 14f; setPadding(0, 0, 0, 8)
        })
        val pass = field("Senha administrativa", true)
        root.addView(pass)
        button(root, "Entrar") {
            val expected = prefs.getString("admin_password", "1234") ?: "1234"
            if (pass.text.toString() == expected) showAdmin() else toast("Senha incorreta.")
        }
        button(root, "Voltar") { showHome() }
    }

    private fun showAdmin() {
        val root = layout("Painel administrativo")
        root.addView(TextView(this).apply { text = "Candidatos: ${candidates.size} | Identificadores que votaram: ${usedVoters.size}"; textSize = 15f })
        button(root, "Cadastrar candidato") { addCandidate() }
        button(root, "Ver apuração") { showResults() }
        button(root, if (electionOpen) "Encerrar votação" else "Reabrir votação") {
            electionOpen = !electionOpen; saveData(); showAdmin()
        }
        button(root, "Alterar senha do administrador") { changePassword() }
        button(root, "Zerar votos e identificadores") {
            AlertDialog.Builder(this).setTitle("Atenção")
                .setMessage("Isso apaga a apuração e permite que os identificadores votem novamente. Use apenas antes de iniciar a eleição.")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Zerar") { _, _ ->
                    candidates.forEach { it.votes = 0 }; usedVoters.clear(); saveData(); showAdmin()
                }.show()
        }
        button(root, "Voltar ao início") { showHome() }
    }

    private fun addCandidate() {
        val root = layout("Cadastrar candidato")
        val number = field("Número do candidato")
        val name = field("Nome do candidato")
        root.addView(number); root.addView(name)
        var photoUri = ""
        val photoLabel = TextView(this).apply { text = "Nenhuma foto selecionada"; setPadding(0, 8, 0, 8) }
        root.addView(photoLabel)
        button(root, "Escolher foto") {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type = "image/*"; addCategory(Intent.CATEGORY_OPENABLE) }
            startActivityForResult(intent, 91)
            pendingPhotoLabel = photoLabel
        }
        button(root, "Salvar candidato") {
            val n = number.text.toString().trim()
            val nm = name.text.toString().trim()
            if (n.isBlank() || nm.isBlank()) toast("Preencha número e nome.")
            else if (candidates.any { it.number == n }) toast("Já existe candidato com esse número.")
            else {
                candidates.add(Candidate(n, nm, selectedPhotoUri, 0)); selectedPhotoUri = ""
                saveData(); showAdmin()
            }
        }
        button(root, "Cancelar") { showAdmin() }
    }

    private var selectedPhotoUri = ""
    private var pendingPhotoLabel: TextView? = null

    @Deprecated("Deprecated in Android API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 91 && resultCode == Activity.RESULT_OK) {
            data?.data?.let { uri ->
                try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
                selectedPhotoUri = uri.toString()
                pendingPhotoLabel?.text = "Foto selecionada"
            }
        }
    }

    private fun showResults() {
        val root = layout("Apuração")
        val total = candidates.sumOf { it.votes }
        root.addView(TextView(this).apply { text = "Total de votos registrados: $total\nIdentificadores que votaram: ${usedVoters.size}"; textSize = 18f; setPadding(0, 0, 0, 14) })
        candidates.sortedByDescending { it.votes }.forEachIndexed { index, c ->
            root.addView(TextView(this).apply {
                text = "${index + 1}º — Nº ${c.number}: ${c.name} — ${c.votes} voto(s)" +
                    if (total > 0) " (${c.votes * 100 / total}%)" else " (0%)"
                textSize = 17f; setPadding(0, 8, 0, 8)
            })
        }
        button(root, "Voltar ao painel") { showAdmin() }
    }

    private fun changePassword() {
        val root = layout("Alterar senha")
        val p1 = field("Nova senha numérica", true)
        val p2 = field("Confirme a nova senha", true)
        root.addView(p1); root.addView(p2)
        button(root, "Salvar senha") {
            val a = p1.text.toString(); val b = p2.text.toString()
            if (a.length < 4) toast("Use pelo menos 4 dígitos.")
            else if (a != b) toast("As senhas não coincidem.")
            else { prefs.edit().putString("admin_password", a).apply(); toast("Senha alterada."); showAdmin() }
        }
        button(root, "Cancelar") { showAdmin() }
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()
}
