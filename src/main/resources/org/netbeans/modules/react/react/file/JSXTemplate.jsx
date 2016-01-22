var CommentBox = React.createClass({
  render: function() {
    return (
      <div className="commentBox">Hello someone... I am a commentBox!</div>
    );
  }
});
ReactDOM.render(
  <CommentBox />,
  document.getElementById('content')
);