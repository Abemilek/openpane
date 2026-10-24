fn main() {
    uniffi::generate_scaffolding("src/openpane_core.udl").expect("generate UniFFI scaffolding");
}
