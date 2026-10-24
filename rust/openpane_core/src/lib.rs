use regex::Regex;
use url::Url;

uniffi::include_scaffolding!("openpane_core");

#[derive(Clone)]
pub struct FilterRules {
    pub muted_words: Vec<String>,
    pub muted_accounts: Vec<String>,
    pub hide_promoted: bool,
}

#[derive(Clone)]
pub struct PostSummary {
    pub id: String,
    pub author: String,
    pub text: String,
    pub promoted: bool,
}

#[allow(non_camel_case_types)]
#[derive(Clone, PartialEq, Debug)]
pub enum Verdict { r#keep, r#hide }

pub struct Engine {
    muted_words: Vec<Regex>,
    muted_accounts: Vec<String>,
    hide_promoted: bool,
}

impl Engine {
    pub fn new(rules: FilterRules) -> Self {
        let muted_words = rules.muted_words.into_iter()
            .filter_map(|word| Regex::new(&word).ok()).collect();
        let muted_accounts = rules.muted_accounts.into_iter()
            .map(|account| account.trim_start_matches('@').to_ascii_lowercase())
            .filter(|account| !account.is_empty()).collect();
        Self { muted_words, muted_accounts, hide_promoted: rules.hide_promoted }
    }

    pub fn evaluate_batch(&self, posts: Vec<PostSummary>) -> Vec<Verdict> {
        posts.into_iter().map(|post| {
            let author = post.author.trim_start_matches('@').to_ascii_lowercase();
            let muted_author = self.muted_accounts.iter().any(|account| account == &author);
            let muted_word = self.muted_words.iter().any(|word| word.is_match(&post.text));
            if (self.hide_promoted && post.promoted) || muted_author || muted_word {
                Verdict::r#hide
            } else { Verdict::r#keep }
        }).collect()
    }
}

pub fn clean_url(input: String) -> String {
    let Ok(mut url) = Url::parse(&input) else { return input };
    let tracking = ["ref", "ref_src", "s", "t", "utm_source", "utm_medium", "utm_campaign"];
    let kept: Vec<(String, String)> = url.query_pairs()
        .filter(|(key, _)| !tracking.contains(&key.as_ref()))
        .map(|(key, value)| (key.into_owned(), value.into_owned())).collect();
    url.query_pairs_mut().clear().extend_pairs(kept);
    url.to_string()
}

#[cfg(test)]
mod tests {
    use super::*;
    fn rules() -> FilterRules {
        FilterRules { muted_words: vec!["spoiler".into(), "(?i)crypto".into()], muted_accounts: vec!["@annoying".into()], hide_promoted: true }
    }
    #[test]
    fn evaluates_posts_in_one_batch() {
        let engine = Engine::new(rules());
        let posts = vec![
            PostSummary { id: "1".into(), author: "friend".into(), text: "hello".into(), promoted: false },
            PostSummary { id: "2".into(), author: "friend".into(), text: "spoiler alert".into(), promoted: false },
            PostSummary { id: "3".into(), author: "@annoying".into(), text: "hello".into(), promoted: false },
            PostSummary { id: "4".into(), author: "brand".into(), text: "hello".into(), promoted: true },
        ];
        assert_eq!(engine.evaluate_batch(posts), vec![Verdict::r#keep, Verdict::r#hide, Verdict::r#hide, Verdict::r#hide]);
    }
    #[test]
    fn ignores_tracking_query_parameters() {
        assert_eq!(clean_url("https://example.com/a?utm_source=x&id=7&t=1".into()), "https://example.com/a?id=7");
    }
    #[test]
    fn preserves_invalid_urls() { assert_eq!(clean_url("not a url".into()), "not a url"); }
}
