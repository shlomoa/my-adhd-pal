import remarkValidateLinks from 'remark-validate-links'

// Internal links only: relative file links and headings. repository:false keeps it independent of the git remote.
export default {
  plugins: [[remarkValidateLinks, {repository: false}]]
}
